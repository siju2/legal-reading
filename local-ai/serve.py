"""Authenticated loopback-only inference. Does not log prompts or train on requests."""
import argparse, hmac, json, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.parse import urlsplit

def search_official_web(query):
    from ddgs import DDGS
    rows=DDGS(timeout=15).text(f'{query} site:law.go.kr OR site:scourt.go.kr OR site:ecrm.police.go.kr OR site:police.go.kr',region='kr-kr',safesearch='moderate',max_results=8,backend='bing')
    results=[]
    for row in rows:
        url=row.get('href','');host=(urlsplit(url).hostname or '').lower()
        if url.startswith('https://') and (host=='law.go.kr' or host.endswith('.law.go.kr') or host=='scourt.go.kr' or host.endswith('.scourt.go.kr') or host=='police.go.kr' or host.endswith('.police.go.kr')):
            results.append({'title':row.get('title','')[:200],'url':url[:2048],'snippet':row.get('body','')[:1600]})
            if len(results)==5:break
    return results

class LocalModel:
    def __init__(self, name):
        self.model_name=name
        self.base_url='http://127.0.0.1:11434'
    def ready(self):
        try:
            with urlopen(self.base_url+'/api/tags',timeout=2) as response:data=json.load(response)
            return any(row.get('name')==self.model_name for row in data.get('models',[]))
        except Exception:return False
    def answer(self,messages,max_tokens,thinking=True):
        options={'num_ctx':8192,'num_predict':max_tokens,'repeat_penalty':1.1}
        options.update({'temperature':0.6,'top_p':0.95,'top_k':20} if thinking else {'temperature':0.7,'top_p':0.8,'top_k':20})
        body=json.dumps({'model':self.model_name,'messages':messages,'think':thinking,'stream':False,'keep_alive':'5m','options':options}).encode()
        request=Request(self.base_url+'/api/chat',data=body,headers={'Content-Type':'application/json'})
        with urlopen(request,timeout=240) as response:data=json.load(response)
        return data.get('message',{}).get('content','').strip()

class Handler(BaseHTTPRequestHandler):
    def setup(self):super().setup();self.connection.settimeout(15)
    def log_message(self,*args):pass
    def send_json(self,status,body):
        data=json.dumps(body,ensure_ascii=False).encode('utf-8');self.send_response(status)
        self.send_header('Content-Type','application/json; charset=utf-8');self.send_header('Content-Length',str(len(data)))
        self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(data)
    def do_GET(self):
        if self.path!='/health':return self.send_json(404,{'error':'Not found'})
        ready=self.server.runtime.ready()
        self.send_json(200 if ready else 503,{'ready':ready,'trained_adapter':getattr(self.server.runtime,'use_adapter',False),'model':self.server.runtime.model_name})
    def do_POST(self):
        try:length=int(self.headers.get('Content-Length','0'))
        except ValueError:return self.send_json(400,{'error':'Invalid body size'})
        if length<=0 or length>65536:
            if 0<length<=65537:self.rfile.read(length)
            return self.send_json(413,{'error':'Invalid body size'})
        payload=self.rfile.read(length)
        if self.path not in ('/chat','/search'):return self.send_json(404,{'error':'Not found'})
        if self.headers.get('Origin'):return self.send_json(403,{'error':'Browser requests are not allowed'})
        if not hmac.compare_digest(self.headers.get('X-Local-AI-Token','').encode(),self.server.token.encode()):return self.send_json(401,{'error':'Unauthorized'})
        if self.headers.get_content_type()!='application/json':return self.send_json(415,{'error':'JSON required'})
        try:
            data=json.loads(payload)
            if self.path=='/search':
                query=data.get('query')
                if not isinstance(query,str) or not query.strip() or len(query)>500:raise ValueError('Invalid search query')
                try:return self.send_json(200,{'results':search_official_web(query.strip())})
                except Exception:return self.send_json(502,{'error':'Web search unavailable'})
            messages=data.get('messages')
            if not isinstance(messages,list) or len(messages)!=2 or [m.get('role') for m in messages]!=['system','user']:raise ValueError('Invalid messages')
            if any(not isinstance(m.get('content'),str) or len(m['content'])>20000 for m in messages):raise ValueError('Invalid content')
            max_tokens=max(64,min(4096,int(data.get('max_tokens',512))))
            thinking=data.get('thinking',True)
            if not isinstance(thinking,bool):raise ValueError('Invalid reasoning mode')
        except (ValueError,TypeError,AttributeError):return self.send_json(400,{'error':'Invalid request'})
        if not self.server.busy.acquire(blocking=False):return self.send_json(429,{'error':'Model busy'})
        try:
            answer=self.server.runtime.answer(messages,max_tokens,thinking)
            if not answer:raise RuntimeError('Empty response')
            self.send_json(200,{'answer':answer})
        except ValueError:self.send_json(413,{'error':'Input exceeds context limit'})
        except Exception:self.send_json(500,{'error':'Local model failed'})
        finally:self.server.busy.release()

def main():
    p=argparse.ArgumentParser();p.add_argument('--model',default='legal-qwen3-8b-candidate:latest');p.add_argument('--token-file',required=True);a=p.parse_args()
    token=Path(a.token_file).read_text(encoding='utf-8-sig').strip()
    if len(token)<32:raise ValueError('A local token with at least 32 characters is required')
    runtime=LocalModel(a.model)
    server=ThreadingHTTPServer(('127.0.0.1',11435),Handler);server.token=token;server.busy=threading.Lock();server.runtime=runtime
    print(f'Local legal AI bridge ready on 127.0.0.1:11435; Ollama model {a.model}',flush=True)
    server.serve_forever()
if __name__=='__main__':main()
