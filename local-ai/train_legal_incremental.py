"""Small LoRA runs with atomic, checked, resumable checkpoints. No active files overwritten."""
import argparse,hashlib,json,math,os,random,signal,time,uuid
from pathlib import Path

def digest(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()

LLAMA_CHAT_TEMPLATE="""{{ bos_token }}{% for message in messages %}{{ '<|start_header_id|>' + message['role'] + '<|end_header_id|>\n\n' + message['content'] | trim + '<|eot_id|>' }}{% endfor %}{% if add_generation_prompt %}{{ '<|start_header_id|>assistant<|end_header_id|>\n\n' }}{% endif %}"""

def atomic_json(path,value):
    path=Path(path); temporary=path.with_name(path.name+'.tmp')
    with temporary.open('w',encoding='utf-8') as file:
        json.dump(value,file,ensure_ascii=False,indent=2);file.flush();os.fsync(file.fileno())
    os.replace(temporary,path)

def checkpoint(output,model,optimizer,step,seen,identity):
    import torch
    output=Path(output);output.mkdir(parents=True,exist_ok=True)
    name=f'checkpoint-{step:05d}'
    target=output/name
    if target.exists():raise FileExistsError('Checkpoint cannot be overwritten: '+str(target))
    staging=output/('.saving-'+uuid.uuid4().hex);staging.mkdir()
    model.save_pretrained(staging,safe_serialization=True)
    state={'optimizer':optimizer.state_dict(),'step':step,'seen':seen,'random':random.getstate(),
           'torch_rng':torch.get_rng_state(),'cuda_rng':torch.cuda.get_rng_state_all() if torch.cuda.is_available() else []}
    torch.save(state,staging/'state.pt')
    hashes={file.name:digest(file) for file in staging.iterdir() if file.is_file()}
    metadata={'identity':identity,'step':step,'seen':seen,'files':hashes}
    atomic_json(staging/'checkpoint.json',metadata)
    for file in staging.iterdir():
        if file.is_file():
            with file.open('r+b') as handle:os.fsync(handle.fileno())
    os.replace(staging,target)
    atomic_json(output/'latest.json',{'checkpoint':name})
    return target

def read_checkpoint(output,identity):
    import torch
    output=Path(output).resolve()
    name=json.loads((output/'latest.json').read_text(encoding='utf-8'))['checkpoint']
    target=(output/name).resolve()
    if target.parent!=output or not target.name.startswith('checkpoint-'):raise ValueError('Invalid checkpoint path')
    metadata=json.loads((target/'checkpoint.json').read_text(encoding='utf-8'))
    if metadata['identity']!=identity:raise ValueError('Dataset or training settings changed; resume refused')
    for name,expected in metadata['files'].items():
        file=(target/name).resolve()
        if file.parent!=target or digest(file)!=expected:raise ValueError('Checkpoint integrity check failed: '+name)
    state=torch.load(target/'state.pt',map_location='cpu',weights_only=True)
    if state['step']!=metadata['step'] or state['seen']!=metadata['seen']:raise ValueError('Checkpoint counters differ')
    return target,state

def restore_training_state(optimizer,state):
    import torch
    optimizer.load_state_dict(state['optimizer']);random.setstate(state['random']);torch.set_rng_state(state['torch_rng'])
    if state['cuda_rng']:torch.cuda.set_rng_state_all(state['cuda_rng'])

def encode(row,tokenizer):
    messages=row['messages']
    prompt=tokenizer.apply_chat_template(messages[:-1],tokenize=True,add_generation_prompt=True,enable_thinking=False)
    answer=tokenizer.encode(messages[-1]['content'],add_special_tokens=False)+[tokenizer.eos_token_id]
    if len(prompt)+len(answer)>768:raise ValueError('Oversize example: '+row['source']['id'])
    return {'input_ids':prompt+answer,'labels':[-100]*len(prompt)+answer}

def main():
    p=argparse.ArgumentParser();p.add_argument('--base',required=True);p.add_argument('--dataset',required=True)
    p.add_argument('--output',required=True);p.add_argument('--batch-size',type=int,default=4)
    p.add_argument('--epochs',type=int,default=1);p.add_argument('--learning-rate',type=float,default=5e-6)
    p.add_argument('--stop-after',type=int,default=0,help='Save and exit after this many steps; next run resumes')
    a=p.parse_args()
    import torch
    from transformers import AutoTokenizer,AutoModelForCausalLM
    from peft import PeftModel,LoraConfig,get_peft_model
    if not torch.cuda.is_available():raise RuntimeError('CUDA required')
    dataset=Path(a.dataset);output=Path(a.output);output.mkdir(parents=True,exist_ok=True)
    data=json.loads(dataset.read_text(encoding='utf-8'))
    if not data.get('train') or not data.get('evaluation'):raise ValueError('Training and holdout examples are required')
    if a.epochs<1 or a.batch_size<1 or a.learning_rate<=0:raise ValueError('Invalid training settings')
    target=len(data['train'])*a.epochs
    config={'batch_size':a.batch_size,'epochs':a.epochs,'lr':a.learning_rate,'rank':4,'alpha':8,'seed':20261119,'save_every_steps':10,'target':target}
    identity={'dataset_sha256':digest(dataset),'config':config,'base_config_sha256':digest(Path(a.base)/'config.json'),
              'tokenizer_sha256':digest(Path(a.base)/'tokenizer_config.json'),'trainer_sha256':digest(__file__)}
    torch.manual_seed(config['seed']);random.seed(config['seed'])
    tokenizer=AutoTokenizer.from_pretrained(a.base,local_files_only=True)
    if not tokenizer.chat_template and 'llama' in str(tokenizer.name_or_path).lower():tokenizer.chat_template=LLAMA_CHAT_TEMPLATE
    training=[encode(r,tokenizer) for r in data['train']];evaluation=[encode(r,tokenizer) for r in data['evaluation']]
    order=sorted(range(len(training)),key=lambda i:len(training[i]['input_ids']))
    batches=[]
    for epoch in range(a.epochs):
        epoch_batches=[order[i:i+a.batch_size] for i in range(0,len(order),a.batch_size)]
        random.Random(config['seed']+epoch).shuffle(epoch_batches);batches.extend(epoch_batches)
    saved=read_checkpoint(output,identity) if (output/'latest.json').exists() else None
    model=AutoModelForCausalLM.from_pretrained(a.base,torch_dtype=torch.bfloat16,local_files_only=True,attn_implementation='sdpa').to('cuda')
    if saved:model=PeftModel.from_pretrained(model,saved[0],is_trainable=True,local_files_only=True)
    else:model=get_peft_model(model,LoraConfig(r=4,lora_alpha=8,lora_dropout=.05,target_modules=['q_proj','v_proj'],task_type='CAUSAL_LM'))
    model.config.use_cache=False;model.gradient_checkpointing_enable();model.enable_input_require_grads()
    optimizer=torch.optim.AdamW([v for v in model.parameters() if v.requires_grad],lr=config['lr'])
    def batch(indices,rows=training):
        selected=[rows[i] for i in indices];width=max(len(r['input_ids']) for r in selected)
        values={k:torch.tensor(v,device='cuda') for k,v in {
            'input_ids':[r['input_ids']+[tokenizer.pad_token_id]*(width-len(r['input_ids'])) for r in selected],
            'attention_mask':[[1]*len(r['input_ids'])+[0]*(width-len(r['input_ids'])) for r in selected],
            'labels':[r['labels']+[-100]*(width-len(r['labels'])) for r in selected]}.items()}
        first=min(next(i for i,v in enumerate(r['labels']) if v!=-100) for r in selected)
        keep=width-first+1
        values['labels']=values['labels'][:,-keep:].contiguous();values['logits_to_keep']=keep
        return values
    def loss_on_holdout():
        model.eval()
        with torch.no_grad():value=sum(model(**batch([i],evaluation)).loss.item() for i in range(len(evaluation)))/len(evaluation)
        model.train();return value
    if saved:
        restore_training_state(optimizer,saved[1]);step=saved[1]['step'];seen=saved[1]['seen']
        before=json.loads((output/'baseline.json').read_text())['evaluation_loss']
    else:
        before=loss_on_holdout();atomic_json(output/'baseline.json',{'evaluation_loss':before,'identity':identity})
        step=seen=0;checkpoint(output,model,optimizer,0,0,identity)
    cancelled=False
    def request_stop(*_):
        nonlocal cancelled
        cancelled=True
    signal.signal(signal.SIGINT,request_stop);signal.signal(signal.SIGTERM,request_stop)
    started=time.time();initial_seen=seen;model.train()
    for index in range(step,len(batches)):
        if cancelled or (output/'STOP').exists():break
        optimizer.zero_grad(set_to_none=True)
        loss=model(**batch(batches[index])).loss
        if not torch.isfinite(loss):raise RuntimeError('Non-finite loss; previous checkpoint preserved')
        loss.backward();torch.nn.utils.clip_grad_norm_(model.parameters(),1.0);optimizer.step()
        step=index+1;seen+=len(batches[index])
        if step%config['save_every_steps']==0 or seen==target or (a.stop_after and step>=a.stop_after):
            checkpoint(output,model,optimizer,step,seen,identity)
            progress={'step':step,'steps':len(batches),'seen':seen,'target':target,'loss':round(loss.item(),5),
                'checkpoint_saved':True,'seconds_this_run':round(time.time()-started),
                'eta_seconds':round((time.time()-started)/max(1,seen-initial_seen)*(target-seen))}
            atomic_json(output/'progress.json',progress);print(json.dumps(progress),flush=True)
        if a.stop_after and step>=a.stop_after:break
    latest,state=read_checkpoint(output,identity)
    if state['step']!=step:latest=checkpoint(output,model,optimizer,step,seen,identity)
    if seen!=target:
        atomic_json(output/'result.json',{'completed':False,'seen':seen,'checkpoint':str(latest),'resume_verified':True})
        print('Saved safely; run the same command to resume.',flush=True);return
    after=loss_on_holdout()
    result={'completed':True,'train_examples':len(training),'epochs':a.epochs,'seen':seen,'evaluation_examples':len(evaluation),
        'evaluation_loss_before':before,'evaluation_loss_after':after,'checkpoint':str(latest),
        'identity':identity,'private_data_used':False,'generation_gate':'pending','published':False}
    atomic_json(output/'result.json',result);print(json.dumps(result),flush=True)

if __name__=='__main__':main()
