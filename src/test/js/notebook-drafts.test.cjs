const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/notebook-drafts.js'), 'utf8');
class Element {
  constructor(options = {}) { Object.assign(this, {listeners:{}, children:[], value:'', type:'textarea', maxLength:3000}, options); }
  addEventListener(name, callback) { (this.listeners[name] ||= []).push(callback); }
  fire(name, event = {}) { for (const fn of this.listeners[name] || []) fn(event); }
  setAttribute() {}
  append(...children) { this.children.push(...children); }
  prepend(child) { this.children.unshift(child); }
  querySelectorAll() { return this.fields; }
}
function storage() {
  return Object.create({getItem(k){return this[k]??null;},setItem(k,v){this[k]=v;},removeItem(k){delete this[k];}});
}
function setup({owner='alice', saved='', shared=storage(), blocked=false, pathname='/notebooks/1/consultation', search=''}={}) {
  const field=new Element({name:'summary',value:saved});
  const token=new Element({name:'_csrf',type:'hidden',value:'secret-csrf'});
  const form=new Element({id:'consultation',dataset:{draftOwner:owner},fields:[field,token]});
  const win=new Element();
  const doc={querySelectorAll:()=>pathname==='/login'?[]:[form],createElement:()=>new Element()};
  const actual=blocked?{getItem(){throw Error('blocked');}}:shared;
  const context={document:doc,window:win,sessionStorage:actual,location:{pathname,search},URLSearchParams};
  return {field,form,win,shared,run(){vm.runInNewContext(source,context);return this;}, get panel(){return form.children[0];}};
}

test('typing never persists private content but warns before navigation',()=>{
 const app=setup().run();app.field.value='비공개 질문';app.form.fire('input');assert.equal(Object.keys(app.shared).length,0);
 let prevented=false;app.win.fire('beforeunload',{preventDefault(){prevented=true;}});assert.equal(prevented,true);
});
test('old plaintext drafts are purged on every load',()=>{
 const shared=storage();shared.setItem('legal-notebook-draft:v1:old','private');shared.setItem('other-app','keep');setup({shared}).run();assert.equal(shared.getItem('legal-notebook-draft:v1:old'),null);assert.equal(shared.getItem('other-app'),'keep');
});
test('submission does not trigger unsaved navigation warning',()=>{
 const app=setup().run();app.field.value='private';app.form.fire('submit');let prevented=false;app.win.fire('beforeunload',{preventDefault(){prevented=true;}});assert.equal(prevented,false);assert.equal(Object.keys(app.shared).length,0);
});
test('blocked storage still allows editing and navigation warning',()=>{
 const app=setup({blocked:true}).run();app.field.value='private';app.form.fire('input');let prevented=false;app.win.fire('beforeunload',{preventDefault(){prevented=true;}});assert.equal(prevented,true);
});
