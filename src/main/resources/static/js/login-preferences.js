(() => {
  const form=document.querySelector('form[data-login-preferences]');
  if(!form)return;
  const username=form.querySelector('[name="username"]');
  const save=form.querySelector('[name="save-username"]');
  const key='legal-reading:saved-username:v1';
  try {
    const saved=localStorage.getItem(key);
    if(saved&&saved.length<=30){username.value=saved;save.checked=true;}
  }catch(_){/* Login works even if browser storage is blocked. */}
  save.addEventListener('change',()=>{
    if(!save.checked)try{localStorage.removeItem(key);}catch(_){}
  });
  form.addEventListener('submit',()=>{
    try{if(save.checked)localStorage.setItem(key,username.value.trim().slice(0,30));else localStorage.removeItem(key);}catch(_){}
  });
})();
