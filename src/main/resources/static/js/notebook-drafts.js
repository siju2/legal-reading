(() => {
  const prefix = 'legal-notebook-draft:v1:';
  if (location.pathname === '/login' && new URLSearchParams(location.search).has('logout')) {
    try { for (const key of Object.keys(sessionStorage)) if (key.startsWith(prefix)) sessionStorage.removeItem(key); } catch (_) { /* Storage may be blocked. */ }
  }
  const states = [];
  for (const form of document.querySelectorAll('form[data-draft]')) {
    const owner = form.dataset.draftOwner;
    if (!owner || !form.id) continue;
    const key = prefix + JSON.stringify([owner, location.pathname, form.id]);
    const fields = [...form.querySelectorAll('input[name],textarea[name],select[name]')]
      .filter(field => !['hidden', 'password', 'file', 'submit', 'button'].includes(field.type));
    const values = () => Object.fromEntries(fields.map(field => [field.name, field.type === 'checkbox' ? field.checked : field.value]));
    const initial = JSON.stringify(values());
    const state = { dirty: false, submitting: false };
    states.push(state);
    const panel = document.createElement('div'); panel.className = 'draft-status';
    const status = document.createElement('p'); status.setAttribute('role', 'status'); status.setAttribute('aria-live', 'polite');
    status.textContent = '입력 중인 내용은 이 브라우저 탭에 임시 저장됩니다. 탭을 닫으면 임시본이 사라집니다.';
    const restore = document.createElement('button'); restore.type = 'button'; restore.className = 'secondary'; restore.textContent = '이전 임시본 복원'; restore.hidden = true;
    const discard = document.createElement('button'); discard.type = 'button'; discard.className = 'text-button'; discard.textContent = '임시본 삭제'; discard.hidden = true;
    panel.append(status, restore, discard); form.prepend(panel);
    let available = true;
    let saved = null;
    try {
      if (form.dataset.draftSaved === form.id) sessionStorage.removeItem(key);
      const raw = sessionStorage.getItem(key);
      if (raw && raw.length <= 100000) {
        const candidate = JSON.parse(raw);
        if (candidate && typeof candidate === 'object' && candidate.values && typeof candidate.values === 'object') saved = candidate;
      }
    } catch (_) { available = false; status.textContent = '이 브라우저에서는 임시 저장을 사용할 수 없습니다. 작성 후 저장 버튼을 눌러 주세요.'; }
    if (saved && JSON.stringify(saved.values) !== initial) {
      restore.hidden = false; discard.hidden = false;
      status.textContent = '이 탭에 이전 임시본이 있습니다. 현재 내용에 덮어쓰려면 복원을 눌러 주세요.';
    }
    const persist = () => {
      const current = values(); state.dirty = JSON.stringify(current) !== initial;
      if (!available) return;
      try {
        if (state.dirty) {
          sessionStorage.setItem(key, JSON.stringify({ savedAt: Date.now(), values: current }));
          status.textContent = '이 탭에 임시 저장했습니다. 서버에 저장하려면 아래 저장 버튼을 눌러 주세요.';
        } else { sessionStorage.removeItem(key); status.textContent = '저장된 내용과 같습니다.'; }
      } catch (_) { available = false; status.textContent = '임시 저장 공간을 사용할 수 없습니다. 저장 버튼을 눌러 주세요.'; }
    };
    restore.addEventListener('click', () => {
      for (const field of fields) {
        const value = saved.values[field.name];
        if (field.type === 'checkbox' && typeof value === 'boolean') field.checked = value;
        else if (typeof value === 'string') field.value = field.maxLength > 0 ? value.slice(0, field.maxLength) : value;
      }
      restore.hidden = true; discard.hidden = true; persist();
    });
    discard.addEventListener('click', () => {
      try { sessionStorage.removeItem(key); } catch (_) { /* Storage may be blocked. */ }
      saved = null; restore.hidden = true; discard.hidden = true;
      status.textContent = '이전 임시본을 삭제했습니다. 현재 입력 내용은 유지됩니다.';
    });
    form.addEventListener('input', persist); form.addEventListener('change', persist);
    form.addEventListener('submit', () => { persist(); state.submitting = true; });
  }
  window.addEventListener('beforeunload', event => {
    if (states.some(state => state.dirty && !state.submitting)) { event.preventDefault(); event.returnValue = ''; }
  });
})();
