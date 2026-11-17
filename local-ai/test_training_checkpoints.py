import json,random,tempfile,unittest,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import torch
from train_legal_incremental import checkpoint,read_checkpoint,restore_training_state

class TinyModel(torch.nn.Linear):
    def save_pretrained(self,directory,**kwargs):torch.save(self.state_dict(),Path(directory)/'weights.pt')

def step(model,optimizer):
    optimizer.zero_grad();inputs=torch.randn(3,2)*random.random()
    loss=model(inputs).square().mean();loss.backward();optimizer.step()

class CheckpointTests(unittest.TestCase):
    def test_resume_matches_uninterrupted_optimizer_and_random_state(self):
        identity={'dataset':'public-fixture','batch_size':4}
        torch.manual_seed(42);random.seed(42);whole=TinyModel(2,2);opt=torch.optim.AdamW(whole.parameters(),lr=.01)
        for _ in range(4):step(whole,opt)
        torch.manual_seed(42);random.seed(42);partial=TinyModel(2,2);opt1=torch.optim.AdamW(partial.parameters(),lr=.01)
        for _ in range(2):step(partial,opt1)
        with tempfile.TemporaryDirectory() as directory:
            checkpoint(directory,partial,opt1,2,8,identity)
            path,state=read_checkpoint(directory,identity)
            resumed=TinyModel(2,2);resumed.load_state_dict(torch.load(path/'weights.pt',weights_only=True))
            opt2=torch.optim.AdamW(resumed.parameters(),lr=.01);restore_training_state(opt2,state)
            for _ in range(2):step(resumed,opt2)
            for expected,actual in zip(whole.parameters(),resumed.parameters()):torch.testing.assert_close(expected,actual,rtol=0,atol=0)
            self.assertEqual(state['seen'],8)
    def test_interrupted_save_preserves_previous_checkpoint(self):
        model=TinyModel(2,2);optimizer=torch.optim.AdamW(model.parameters())
        with tempfile.TemporaryDirectory() as directory:
            checkpoint(directory,model,optimizer,0,0,{'dataset':'a'})
            class Broken(TinyModel):
                def save_pretrained(self,path,**kwargs):
                    (Path(path)/'partial').write_text('incomplete');raise OSError('Simulated disk interruption')
            with self.assertRaises(OSError):checkpoint(directory,Broken(2,2),optimizer,1,4,{'dataset':'a'})
            path,state=read_checkpoint(directory,{'dataset':'a'})
            self.assertEqual(path.name,'checkpoint-00000');self.assertEqual(state['seen'],0)
    def test_corruption_and_dataset_changes_are_rejected(self):
        model=TinyModel(2,2);optimizer=torch.optim.AdamW(model.parameters())
        with tempfile.TemporaryDirectory() as directory:
            path=checkpoint(directory,model,optimizer,0,0,{'dataset':'a'})
            with self.assertRaisesRegex(ValueError,'settings changed'):read_checkpoint(directory,{'dataset':'b'})
            with (path/'state.pt').open('ab') as file:file.write(b'broken')
            with self.assertRaisesRegex(ValueError,'integrity'):read_checkpoint(directory,{'dataset':'a'})
    def test_checkpoint_cannot_be_overwritten_or_escape_output(self):
        model=TinyModel(2,2);optimizer=torch.optim.AdamW(model.parameters())
        with tempfile.TemporaryDirectory() as directory:
            checkpoint(directory,model,optimizer,0,0,{})
            with self.assertRaises(FileExistsError):checkpoint(directory,model,optimizer,0,0,{})
            (Path(directory)/'latest.json').write_text(json.dumps({'checkpoint':'../outside'}))
            with self.assertRaisesRegex(ValueError,'Invalid checkpoint path'):read_checkpoint(directory,{})

if __name__=='__main__':unittest.main()
