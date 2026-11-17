const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/login-preferences.js'),'utf8');
function setup({saved=null,blocked=false}={}){
 const data=new Map(saved?[['legal-reading:saved-username:v1',saved]]:[]),listeners={},user={value:''},save={checked:false,addEventListener:(e,f)=>listeners[e]=f};
 const form={querySelector:s=>s.includes('username"]')?user:save,addEventListener:(e,f)=>listeners[e]=f};
 // Match exact names; never expose a password field to the persistence code.
 form.querySelector=s=>s==='[name="username"]'?user:save;
 const storage={getItem:k=>{if(blocked)throw Error();return data.get(k)??null;},setItem:(k,v)=>{if(blocked)throw Error();data.set(k,v);},removeItem:k=>{if(blocked)throw Error();data.delete(k);}};
 vm.runInNewContext(source,{document:{querySelector:()=>form},localStorage:storage});return {data,listeners,user,save};
}
test('saved ID restores with explicit checkbox',()=>{const s=setup({saved:'demo_legal'});assert.equal(s.user.value,'demo_legal');assert.equal(s.save.checked,true);});
test('unchecked ID is removed immediately',()=>{const s=setup({saved:'demo_legal'});s.save.checked=false;s.listeners.change();assert.equal(s.data.size,0);});
test('only opted-in username is persisted',()=>{const s=setup();s.user.value='demo_legal';s.listeners.submit();assert.equal(s.data.size,0);s.save.checked=true;s.listeners.submit();assert.deepEqual([...s.data.values()],['demo_legal']);});
test('blocked browser storage does not interrupt submission',()=>{const s=setup({blocked:true});s.save.checked=true;assert.doesNotThrow(()=>s.listeners.submit());});
