import json, threading, unittest, urllib.request, urllib.error
from http.server import ThreadingHTTPServer
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from serve import Handler
class FakeModel:
    def answer(self,messages,max_tokens):return '로컬 테스트 답변'
class ApiTests(unittest.TestCase):
    def setUp(self):
        self.server=ThreadingHTTPServer(('127.0.0.1',0),Handler);self.server.token='test-only-token-'+'x'*32;self.server.busy=threading.Lock();self.server.runtime=FakeModel()
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start();self.url='http://127.0.0.1:'+str(self.server.server_port)+'/chat'
    def tearDown(self):self.server.shutdown();self.server.server_close();self.thread.join()
    def request(self,token=None,origin=None,data=None):
        headers={'Content-Type':'application/json','X-Local-AI-Token':token or self.server.token}
        if origin:headers['Origin']=origin
        body=data or {'messages':[{'role':'system','content':'test'},{'role':'user','content':'질문'}]}
        req=urllib.request.Request(self.url,json.dumps(body).encode(),headers)
        try:
            with urllib.request.urlopen(req) as response:return response.status,json.load(response)
        except urllib.error.HTTPError as e:return e.code,json.load(e)
    def test_auth_origin_and_payload_boundaries(self):
        self.assertEqual(self.request(token='wrong')[0],401)
        self.assertEqual(self.request(origin='https://other.invalid')[0],403)
        self.assertEqual(self.request(data={'messages':[]})[0],400)
        self.assertEqual(self.request(data={'messages':[None,None]})[0],400)
        self.assertEqual(self.request(data={'messages':'x'*65521})[0],413)
        self.assertEqual(self.request(),(200,{'answer':'로컬 테스트 답변'}))
    def test_busy_model_does_not_queue_unbounded_requests(self):
        self.server.busy.acquire()
        try:self.assertEqual(self.request()[0],429)
        finally:self.server.busy.release()
    def test_health_does_not_require_a_trained_adapter_to_be_ready(self):
        url=self.url.replace('/chat','/health')
        with urllib.request.urlopen(url) as response: health=json.load(response)
        self.assertTrue(health['ready'])
        self.assertFalse(health['trained_adapter'])
        self.server.runtime.use_adapter=True
        with urllib.request.urlopen(url) as response: health=json.load(response)
        self.assertTrue(health['trained_adapter'])
if __name__=='__main__':unittest.main()
