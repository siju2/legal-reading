"""Authenticated loopback-only inference. Does not log prompts or train on requests."""
import argparse, hmac, json, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

class LocalModel:
    def __init__(self, base, adapter, use_adapter=False):
        import torch
        from transformers import AutoTokenizer, AutoModelForCausalLM
        if not torch.cuda.is_available():raise RuntimeError('CUDA GPU not available')
        self.torch=torch
        self.tokenizer=AutoTokenizer.from_pretrained(base,local_files_only=True)
        model=AutoModelForCausalLM.from_pretrained(base,torch_dtype=torch.bfloat16,local_files_only=True,attn_implementation='sdpa').to('cuda')
        self.use_adapter=use_adapter
        if use_adapter:
            from peft import PeftModel
            model=PeftModel.from_pretrained(model,adapter,local_files_only=True)
        self.model=model.eval()
    def answer(self,messages,max_tokens):
        inputs=self.tokenizer.apply_chat_template(messages,add_generation_prompt=True,enable_thinking=False,tokenize=True,return_dict=True,return_tensors='pt').to('cuda')
        if inputs['input_ids'].shape[1]>8192:raise ValueError('Input too long')
        with self.torch.inference_mode():
            output=self.model.generate(**inputs,max_new_tokens=max_tokens,do_sample=False,repetition_penalty=1.08,pad_token_id=self.tokenizer.eos_token_id)
        return self.tokenizer.decode(output[0][inputs['input_ids'].shape[1]:],skip_special_tokens=True).strip()

class Handler(BaseHTTPRequestHandler):
    def setup(self):super().setup();self.connection.settimeout(15)
    def log_message(self,*args):pass
    def send_json(self,status,body):
        data=json.dumps(body,ensure_ascii=False).encode('utf-8');self.send_response(status)
        self.send_header('Content-Type','application/json; charset=utf-8');self.send_header('Content-Length',str(len(data)))
        self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(data)
    def do_GET(self):
        if self.path!='/health':return self.send_json(404,{'error':'Not found'})
        self.send_json(200,{'ready':True,'trained_adapter':getattr(self.server.runtime,'use_adapter',False),'model':'Qwen3-4B local'})
    def do_POST(self):
        try:length=int(self.headers.get('Content-Length','0'))
        except ValueError:return self.send_json(400,{'error':'Invalid body size'})
        if length<=0 or length>65536:
            if 0<length<=65537:self.rfile.read(length)
            return self.send_json(413,{'error':'Invalid body size'})
        payload=self.rfile.read(length)
        if self.path!='/chat':return self.send_json(404,{'error':'Not found'})
        if self.headers.get('Origin'):return self.send_json(403,{'error':'Browser requests are not allowed'})
        if not hmac.compare_digest(self.headers.get('X-Local-AI-Token','').encode(),self.server.token.encode()):return self.send_json(401,{'error':'Unauthorized'})
        if self.headers.get_content_type()!='application/json':return self.send_json(415,{'error':'JSON required'})
        try:
            data=json.loads(payload);messages=data.get('messages')
            if not isinstance(messages,list) or len(messages)!=2 or [m.get('role') for m in messages]!=['system','user']:raise ValueError('Invalid messages')
            if any(not isinstance(m.get('content'),str) or len(m['content'])>20000 for m in messages):raise ValueError('Invalid content')
            max_tokens=max(64,min(768,int(data.get('max_tokens',512))))
        except (ValueError,TypeError,AttributeError):return self.send_json(400,{'error':'Invalid request'})
        if not self.server.busy.acquire(blocking=False):return self.send_json(429,{'error':'Model busy'})
        try:
            answer=self.server.runtime.answer(messages,max_tokens)
            if not answer:raise RuntimeError('Empty response')
            self.send_json(200,{'answer':answer})
        except ValueError:self.send_json(413,{'error':'Input exceeds context limit'})
        except Exception:self.send_json(500,{'error':'Local model failed'})
        finally:self.server.busy.release()

def main():
    p=argparse.ArgumentParser();p.add_argument('--base',required=True);p.add_argument('--adapter',required=True);p.add_argument('--token-file',required=True);p.add_argument('--use-adapter',action='store_true');a=p.parse_args()
    token=Path(a.token_file).read_text(encoding='utf-8-sig').strip()
    if len(token)<32:raise ValueError('A local token with at least 32 characters is required')
    runtime=LocalModel(a.base,a.adapter,a.use_adapter)
    server=ThreadingHTTPServer(('127.0.0.1',11435),Handler);server.token=token;server.busy=threading.Lock();server.runtime=runtime
    print('Local legal model ready on 127.0.0.1:11435',flush=True)
    server.serve_forever()
if __name__=='__main__':main()
