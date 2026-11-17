"""One-pass, source-grounded LoRA expansion; leaves the active adapter untouched."""
import argparse, hashlib, json, math, random, time
from pathlib import Path

SYSTEM = '한국어 법률 정보 도우미입니다. 제공된 공식 자료만 근거로 답하고 [1]을 인용하세요. 발췌문을 전체 법률이나 판결로 단정하지 마세요. 없는 사실·판례·조문을 만들거나 결과를 보장하지 마세요.'

def example(source, tokenizer, index):
    text = source['text'][:320].strip()
    task = ('이 자료에서 확인되는 내용을 근거와 함께 알려줘.', '관련 자료를 읽고 있는데, 이 부분은 뭐라고 되어 있어?',
            '이 자료에 실제로 적힌 내용을 확인해 줘.', '이 내용을 내 사건에 적용하기 전에 무엇을 주의해야 해?')[index % 4]
    while True:
        excerpt = text[:160].strip()
        if len(text)>len(excerpt): excerpt += '…'
        kind = '판결의 일부 발췌' if source['kind']=='case' else '조문의 일부 발췌'
        title=source['title'][:120]
        if source['kind']=='case': title += ' / 사건번호 '+source['caseNumber']+' / 선고일 '+source['decisionDate']
        user = f"{task}\n[1] {title}\n{text}"
        answer = f"제공된 자료 [1]에는 다음 내용이 있습니다.\n「{excerpt}」\n이는 {kind}입니다. 적용 시점과 원문의 앞뒤 내용, 구체적인 사실관계를 함께 확인해야 합니다. 이 자료만으로 개별 사건의 결과를 확정할 수 없습니다."
        messages = [{'role':'system','content':SYSTEM},{'role':'user','content':user},{'role':'assistant','content':answer}]
        prompt=tokenizer.apply_chat_template(messages[:-1], tokenize=True, add_generation_prompt=True, enable_thinking=False)
        response=tokenizer.encode(answer,add_special_tokens=False)+[tokenizer.eos_token_id]
        if len(prompt)+len(response)<=640: break
        text=text[:-20].strip()
        if len(text)<80: raise ValueError('Citation/title leaves insufficient source context: '+source['id'])
    return {'messages':messages,'source':{k:source[k] for k in ('id','documentId','kind','url','textSha256')},
            'input_ids':prompt+response,'labels':[-100]*len(prompt)+response}

def prepare(corpus, tokenizer, output):
    random.seed(20261116)
    laws=[r for r in corpus if r['kind']=='law']; cases=[r for r in corpus if r['kind']=='case']
    random.shuffle(laws); random.shuffle(cases)
    case_docs=list(dict.fromkeys(r['documentId'] for r in cases))
    holdout_docs=set(case_docs[:max(10,len(case_docs)//20)])
    case_eval=[r for r in cases if r['documentId'] in holdout_docs][:100]
    case_train=[r for r in cases if r['documentId'] not in holdout_docs]
    assert len(case_train)>=2000 and len(laws)>=18100
    selected=laws[100:18100]+case_train[:2000]
    evaluation=laws[:100]+case_eval
    assert len(selected)==20000 and not ({r['id'] for r in selected}&{r['id'] for r in evaluation})
    assert not ({r['documentId'] for r in selected if r['kind']=='case'} & holdout_docs)
    random.shuffle(selected)
    encoded={}
    for name,rows in [('train',selected),('evaluation',evaluation)]:
        encoded[name]=[example(row,tokenizer,i) for i,row in enumerate(rows)]
        # Length bucketing reduces padding; every selected example still receives a gradient.
        if name=='train': encoded[name].sort(key=lambda r:len(r['input_ids']))
        (output/(name+'.jsonl')).write_text(''.join(json.dumps({k:v for k,v in r.items() if k not in ('input_ids','labels')},ensure_ascii=False)+'\n' for r in encoded[name]),encoding='utf-8')
    manifest={'train_examples':20000,'law_articles_trained':18000,'case_passages_trained':2000,
        'distinct_cases_trained':len({r['documentId'] for r in selected if r['kind']=='case'}),
        'distinct_laws_trained':len({r['documentId'] for r in selected if r['kind']=='law'}),
        'evaluation_examples':len(evaluation),'case_document_holdout':True,
        'source_corpus_sha256':hashlib.sha256(json.dumps(corpus,ensure_ascii=False,sort_keys=True).encode()).hexdigest(),
        'scope':'Exact official source excerpts and citation/uncertainty behavior; not expert legal validation',
        'private_data_used':False,'max_tokens':max(len(r['input_ids']) for r in encoded['train'])}
    (output/'dataset-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(manifest,ensure_ascii=False),flush=True)
    return encoded,manifest

def answer_logits_only(inputs, first_answer):
    # Prompt labels are ignored; skip their large vocabulary projection as well.
    keep=inputs['input_ids'].shape[1]-first_answer+1
    return {**inputs,'labels':inputs['labels'][:,-keep:].contiguous(),'logits_to_keep':keep}

def main():
    p=argparse.ArgumentParser(); p.add_argument('--base',required=True);p.add_argument('--adapter',required=True)
    p.add_argument('--corpus',required=True);p.add_argument('--output',required=True);p.add_argument('--prepare-only',action='store_true')
    p.add_argument('--batch-size',type=int,default=4);p.add_argument('--benchmark-steps',type=int,default=0)
    a=p.parse_args()
    import torch
    from transformers import AutoTokenizer,AutoModelForCausalLM
    from peft import PeftModel
    if a.batch_size<1: raise ValueError('Invalid batch size')
    output=Path(a.output);output.mkdir(parents=True,exist_ok=True)
    torch.manual_seed(42)
    tokenizer=AutoTokenizer.from_pretrained(a.base,local_files_only=True)
    encoded,manifest=prepare(json.loads(Path(a.corpus).read_text(encoding='utf-8')),tokenizer,output)
    if a.prepare_only:return
    if not torch.cuda.is_available():raise RuntimeError('CUDA required')
    model=AutoModelForCausalLM.from_pretrained(a.base,torch_dtype=torch.bfloat16,local_files_only=True,attn_implementation='sdpa').to('cuda')
    model=PeftModel.from_pretrained(model,a.adapter,is_trainable=True,local_files_only=True)
    model.config.use_cache=False; model.gradient_checkpointing_enable();model.enable_input_require_grads()
    optimizer=torch.optim.AdamW([v for v in model.parameters() if v.requires_grad],lr=2e-5)
    def batch(rows):
        width=max(len(r['input_ids']) for r in rows)
        inputs={k:torch.tensor(v,device='cuda') for k,v in {
            'input_ids':[r['input_ids']+[tokenizer.pad_token_id]*(width-len(r['input_ids'])) for r in rows],
            'attention_mask':[[1]*len(r['input_ids'])+[0]*(width-len(r['input_ids'])) for r in rows],
            'labels':[r['labels']+[-100]*(width-len(r['labels'])) for r in rows]}.items()}
        return answer_logits_only(inputs,min(next(i for i,label in enumerate(r['labels']) if label!=-100) for r in rows))
    def evaluate():
        model.eval()
        with torch.no_grad():loss=sum(model(**batch([r])).loss.item() for r in encoded['evaluation'])/len(encoded['evaluation'])
        model.train();return loss
    before=evaluate();started=time.time();steps=math.ceil(len(encoded['train'])/a.batch_size);seen=0
    # Shuffle batches, keeping similarly sized inputs together to avoid wasted padding.
    batches=[encoded['train'][i:i+a.batch_size] for i in range(0,len(encoded['train']),a.batch_size)]
    random.shuffle(batches)
    model.train()
    for step,rows in enumerate(batches,1):
        optimizer.zero_grad(set_to_none=True);loss=model(**batch(rows)).loss
        if not torch.isfinite(loss): raise RuntimeError('Non-finite loss')
        loss.backward();torch.nn.utils.clip_grad_norm_(model.parameters(),1.0);optimizer.step();seen+=len(rows)
        if step==1 or step%25==0 or step==steps:
            progress={'step':step,'steps':steps,'examples_seen':seen,'loss':round(loss.item(),5),'seconds':round(time.time()-started),'eta_seconds':round((time.time()-started)/step*(steps-step))}
            (output/'progress.json').write_text(json.dumps(progress),encoding='utf-8');print(json.dumps(progress),flush=True)
        if a.benchmark_steps and step>=a.benchmark_steps:
            print('Benchmark only; adapter not published',flush=True);return
        if step%1000==0: model.save_pretrained(output/'checkpoint',safe_serialization=True)
    assert seen==20000
    after=evaluate()
    if not math.isfinite(after) or after>before*1.2:raise RuntimeError(f'Validation regression: {before} -> {after}')
    model.save_pretrained(output/'adapter',safe_serialization=True)
    metrics={**manifest,'base':'Qwen/Qwen3-4B','steps':steps,'examples_seen':seen,'batch_size':a.batch_size,
        'evaluation_loss_before':before,'evaluation_loss_after':after,'seconds':time.time()-started,
        'gpu':torch.cuda.get_device_name(0),'completed':True}
    (output/'metrics.json').write_text(json.dumps(metrics,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(metrics,ensure_ascii=False),flush=True)

if __name__=='__main__':main()
