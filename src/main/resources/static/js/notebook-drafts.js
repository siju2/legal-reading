(() => {
  try { for (const key of Object.keys(sessionStorage)) if (key.startsWith('legal-notebook-draft:')) sessionStorage.removeItem(key); } catch (_) {}
  const states=[];
  for(const form of document.querySelectorAll('form[data-draft]')) {
    const fields=[...form.querySelectorAll('input[name],textarea[name],select[name]')].filter(f=>!['hidden','password','file','submit','button'].includes(f.type));
    const values=()=>JSON.stringify(fields.map(f=>[f.name,f.type==='checkbox'?f.checked:f.value]));
    const initial=values(),state={dirty:false,submitting:false}; states.push(state);
    const note=document.createElement('p');note.setAttribute('role','status');note.textContent='개인정보 보호를 위해 브라우저 임시 저장을 사용하지 않습니다. 작성 후 저장 버튼을 눌러 주세요.';form.prepend(note);
    const update=()=>{state.dirty=values()!==initial;};
    form.addEventListener('input',update);form.addEventListener('change',update);form.addEventListener('submit',()=>{update();state.submitting=true;});
  }
  window.addEventListener('beforeunload',event=>{if(states.some(s=>s.dirty&&!s.submitting)){event.preventDefault();event.returnValue='';}});
})();
