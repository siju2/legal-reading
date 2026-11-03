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
test('typing persists draft without CSRF and warns before unsaved navigation',()=>{
  const app=setup().run();app.field.value='작성 중인 상담 질문';app.form.fire('input');
  const raw=app.shared[Object.keys(app.shared)[0]];assert.match(raw,/작성 중인 상담 질문/);assert.doesNotMatch(raw,/csrf|secret/);
  let prevented=false;app.win.fire('beforeunload',{preventDefault(){prevented=true;}});assert.equal(prevented,true);
});
test('reload offers explicit recovery without overwriting server text',()=>{
  const first=setup().run();first.field.value='복원할 내용';first.form.fire('input');
  const next=setup({shared:first.shared,saved:'서버의 기존 내용'}).run();assert.equal(next.field.value,'서버의 기존 내용');
  assert.equal(next.panel.children[1].hidden,false);next.panel.children[1].fire('click');assert.equal(next.field.value,'복원할 내용');
});
test('successful save clears only the matching form draft',()=>{
  const first=setup().run();first.field.value='draft';first.form.fire('input');
  const next=setup({shared:first.shared,saved:'draft'});next.form.dataset.draftSaved='consultation';next.run();assert.equal(Object.keys(next.shared).length,0);
});
test('different accounts cannot recover each other drafts',()=>{
  const first=setup().run();first.field.value='alice-private';first.form.fire('input');
  const bob=setup({owner:'bob',shared:first.shared}).run();assert.equal(bob.panel.children[1].hidden,true);assert.equal(bob.field.value,'');
});
test('blocked storage retains navigation warning',()=>{
  const app=setup({blocked:true}).run();app.field.value='unsaved';app.form.fire('input');let prevented=false;
  app.win.fire('beforeunload',{preventDefault(){prevented=true;}});assert.equal(prevented,true);assert.match(app.panel.children[0].textContent,/임시 저장을 사용할 수 없습니다/);
});
test('submitting preserves recovery data until server confirms success',()=>{
  const app=setup().run();app.field.value='서버 실패 시 복원할 내용';app.form.fire('submit');let prevented=false;
  app.win.fire('beforeunload',{preventDefault(){prevented=true;}});assert.equal(prevented,false);assert.equal(Object.keys(app.shared).length,1);
  const failure=setup({shared:app.shared}).run();assert.equal(failure.panel.children[1].hidden,false);
});
test('successful logout clears personal drafts in this tab',()=>{
  const app=setup().run();app.field.value='personal';app.form.fire('input');setup({shared:app.shared,pathname:'/login',search:'?logout'}).run();assert.equal(Object.keys(app.shared).length,0);
});
