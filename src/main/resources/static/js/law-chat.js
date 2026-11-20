const form = document.getElementById('law-chat');
const messages = document.getElementById('law-messages');
const status = document.getElementById('law-chat-status');
const reset = document.getElementById('law-chat-reset');
const input = document.getElementById('law-question');
const send = form.querySelector('button[type="submit"],button:not([type])');
let conversation = [];
let scope = '';
const welcome = document.getElementById('law-chat-welcome');
const history = document.getElementById('law-chat-history');
const historyEmpty = document.getElementById('law-history-empty');
const saveButton = document.getElementById('law-chat-save');
const deleteButton = document.getElementById('law-chat-delete');
const sessionField = document.getElementById('law-session-id');
const savedList = document.getElementById('law-saved-chats');
const savedEmpty = document.getElementById('law-saved-empty');
const notebook = document.getElementById('law-notebook');
const turns = [];
let messageId = 0;
let dirty = false;
function updateWelcome() {
    if (welcome) welcome.hidden = messages.childElementCount > 0 || !!document.querySelector('.initial-answer');
    if (historyEmpty) historyEmpty.hidden = history.childElementCount > 0;
}
function clearMessages() {
    messages.replaceChildren();
    history?.replaceChildren();
    document.querySelector('.initial-answer')?.remove();
    updateWelcome();
}
document.querySelectorAll('[data-chat-prompt]').forEach(button => button.addEventListener('click', () => {
    input.value = button.dataset.chatPrompt;
    input.focus();
    input.scrollIntoView({block: 'nearest'});
}));
updateWelcome();
reset.hidden = false;
scope = (notebook?.value || '') + ':' + !!form.querySelector('input[name="consent"]')?.checked;

notebook?.addEventListener('change', () => {
    const oldNotebook = scope.split(':')[0];
    if (!oldNotebook && notebook.value) {
        scope = notebook.value + ':' + !!form.querySelector('input[name="consent"]')?.checked;
        status.textContent = '사건 노트를 선택했습니다. 현재 대화를 저장할 수 있습니다.';
        return;
    }
    if (dirty && !window.confirm('노트를 바꾸면 저장하지 않은 대화가 초기화됩니다. 계속할까요?')) {
        window.location.reload();
        return;
    }
    window.location.assign(notebook.value ? '/legal-ai?notebookId=' + encodeURIComponent(notebook.value) : '/legal-ai');
});

function message(label, text, user = false) {
    const card = document.createElement('article');
    card.className = user ? 'law-message law-message-user' : 'law-message';
    card.dataset.role = user ? '사용자' : 'AI';
    card.dataset.text = text;
    const heading = document.createElement('strong');
    heading.textContent = label;
    const body = document.createElement('p');
    body.textContent = text;
    card.append(heading, body);
    messages.append(card);
    updateWelcome();
    return card;
}

for (const card of messages.querySelectorAll('.law-message')) {
    const role = card.dataset.role;
    const text = card.dataset.text || '';
    const sources = [...card.querySelectorAll('.saved-chat-source')].map(link => ({
        id: link.dataset.id, title: link.dataset.title, url: link.dataset.url,
        checkedAt: link.dataset.checkedAt, text: link.parentElement.querySelector('p')?.textContent || ''
    }));
    turns.push({role, text, sources});
    conversation.push({role, text});
    if (role === '사용자') {
        const item = document.createElement('li');
        const link = document.createElement('a');
        link.href = '#saved-turn-' + (++messageId);
        link.textContent = text;
        card.id = link.hash.slice(1);
        item.append(link);
        history?.append(item);
    }
}
conversation = conversation.slice(-6);
updateWelcome();

function updateSavedList(summary) {
    if (!savedList) return;
    const item = document.createElement('li');
    const link = document.createElement('a');
    const url = new URL('/legal-ai', window.location.origin);
    url.searchParams.set('notebookId', notebook.value);
    url.searchParams.set('sessionId', summary.id);
    link.href = url;
    link.textContent = summary.title;
    item.append(link);
    savedList.prepend(item);
    if (savedEmpty) savedEmpty.hidden = true;
}

saveButton?.addEventListener('click', async () => {
    if (!notebook?.value) {
        document.querySelector('.note-options').open = true;
        notebook?.focus();
        status.textContent = '먼저 추가 설정에서 저장할 사건 노트를 선택해 주세요.';
        return;
    }
    if (!turns.length) { status.textContent = '저장할 대화가 아직 없습니다.'; return; }
    const payload = new URLSearchParams();
    const csrf = form.querySelector('input[name="_csrf"]')?.value;
    if (csrf) payload.set('_csrf', csrf);
    payload.set('notebookId', notebook.value);
    if (sessionField.value) payload.set('sessionId', sessionField.value);
    payload.set('turns', JSON.stringify(turns));
    saveButton.disabled = true;
    status.textContent = '대화를 암호화해 저장하고 있습니다.';
    try {
        const response = await fetch('/legal-ai/sessions', {method: 'POST', body: payload});
        if (!response.ok) throw new Error();
        const summary = await response.json();
        const isNew = !sessionField.value;
        sessionField.value = summary.id;
        dirty = false;
        if (isNew) updateSavedList(summary);
        const url = new URL(window.location.href);
        url.searchParams.set('notebookId', notebook.value);
        url.searchParams.set('sessionId', summary.id);
        window.history.replaceState({}, '', url);
        status.textContent = '선택한 사건 노트에 대화를 저장했습니다.';
    } catch {
        status.textContent = '저장하지 못했습니다. 노트 선택과 연결 상태를 확인해 주세요.';
    } finally { saveButton.disabled = false; }
});

deleteButton?.addEventListener('click', async () => {
    if (!sessionField.value || !notebook?.value || !window.confirm('이 저장 대화를 삭제할까요?')) return;
    const payload = new URLSearchParams({notebookId: notebook.value});
    const csrf = form.querySelector('input[name="_csrf"]')?.value;
    if (csrf) payload.set('_csrf', csrf);
    deleteButton.disabled = true;
    try {
        const response = await fetch('/legal-ai/sessions/' + encodeURIComponent(sessionField.value) + '/delete', {method: 'POST', body: payload});
        if (!response.ok) throw new Error();
        const url = new URL('/legal-ai', window.location.origin);
        url.searchParams.set('notebookId', notebook.value);
        window.location.assign(url);
    } catch {
        status.textContent = '저장 대화를 삭제하지 못했습니다. 다시 시도해 주세요.';
        deleteButton.disabled = false;
    }
});

reset.addEventListener('click', () => {
    clearMessages();
    conversation = [];
    turns.length = 0;
    dirty = false;
    sessionField.value = '';
    const url = new URL(window.location.href);
    url.searchParams.delete('sessionId');
    window.history.replaceState({}, '', url);
    status.textContent = '';
    input.value = '';
    input.focus();
});

form.addEventListener('submit', async event => {
    event.preventDefault();
    if (send.disabled || !input.value.trim()) return;
    const data = new URLSearchParams(new FormData(form));
    const nextScope = data.get('notebookId') + ':' + data.has('consent');
    if (nextScope !== scope) { conversation = []; turns.length = 0; sessionField.value = ''; dirty = false; clearMessages(); }
    scope = nextScope;
    if (turns.length + 2 > 50) {
        status.textContent = '이 대화는 25회까지 저장할 수 있습니다. 대화를 저장한 뒤 새 대화를 시작해 주세요.';
        return;
    }
    const question = input.value.trim();
    data.set('question', question);
    data.set('context', conversation.slice(-6).map(turn => turn.role + ': ' + turn.text).join('\n').slice(-12000));
    const pending = message('나', question, true);
    send.disabled = reset.disabled = true;
    input.disabled = true;
    const deepAnalysis = data.get('mode') === 'deep';
    status.textContent = deepAnalysis ? '관련 근거와 조건을 확인해 자세한 답변을 준비하고 있습니다.' : '관련 근거를 찾아 핵심을 정리하고 있습니다.';
    try {
        const response = await fetch(form.action + '/chat', {method: 'POST', body: data});
        if (response.redirected) {
            window.location.assign(response.url);
            return;
        }
        if (!response.ok) {
            status.textContent = {
                400: '질문과 사건 노트 동의 여부를 확인해 주세요.',
                429: '요청 한도에 도달했거나 AI가 답변 중입니다. 잠시 후 다시 시도해 주세요.',
                413: '대화가 로컬 AI 입력 한도를 넘었습니다. 새 대화를 시작하거나 질문과 사건 노트 내용을 줄여 주세요.',
                503: '로컬 AI가 연결되지 않았습니다.'
            }[response.status] || '답변을 받지 못했습니다. 다시 시도해 주세요.';
            return;
        }
        const answer = await response.json();

        const card = message('법률 AI · ' + (deepAnalysis ? '생각 모드 · 자세한 답변' : '빠른 답변'), answer.text);
        const comparing = /비교|차이/.test(question) && answer.sources.length > 1;
        if (comparing) card.classList.add('law-message-comparison');
        if (answer.sources.length) {
            const details = document.createElement('details');
            const summary = document.createElement('summary');
            const liveCount = answer.sources.filter(source => source.id.startsWith('web-search-')).length;
            const linkCount = answer.sources.filter(source => source.id.startsWith('online-search-')).length;
            summary.textContent = linkCount === answer.sources.length
                ? '공식 검색 페이지 ' + linkCount + '개'
                : '관련 법률 자료 ' + answer.sources.length + '건' + (liveCount ? ' · 실시간 검색 미리보기 포함' : '');
            const list = document.createElement('ol');
            for (const source of answer.sources) {
                const item = document.createElement('li');
                const link = document.createElement('a');
                link.href = source.url;
                link.target = '_blank';
                link.rel = 'noopener noreferrer';
                link.textContent = source.title;
                const text = document.createElement('p');
                text.textContent = source.text;
                const checked = document.createElement('small');
                checked.textContent = source.id.startsWith('web-search-') ? '실시간 공식 웹 검색 미리보기 · 원문 열기' : source.id.startsWith('online-search-') ? '공식 검색 페이지 · 클릭하면 최신 검색 결과' : '자료 확인일 ' + source.checkedAt;
                item.append(link, checked, text);
                list.append(item);
            }
            details.className = 'source-details';
            details.open = comparing || !answer.generated;
            list.className = 'source-cards';
            details.append(summary, list);
            card.append(details);
        }
        turns.push({role: '사용자', text: question, sources: []}, {role: 'AI', text: answer.text, sources: answer.sources || []});
        dirty = true;
        conversation = [...conversation.slice(-4), {role: '사용자', text: question}, {role: 'AI', text: answer.text.slice(0, 3000)}];
        pending.dataset.sent = 'true';
        pending.id = 'law-turn-' + (++messageId);
        const item = document.createElement('li');
        const link = document.createElement('a');
        link.href = '#' + pending.id;
        link.textContent = question;
        link.title = question;
        item.append(link);
        history?.append(item);
        updateWelcome();
        if (input.value.trim() === question) input.value = '';
        status.textContent = '';
        card.scrollIntoView({block: 'nearest'});
        input.focus({preventScroll: true});
    } catch {
        status.textContent = '연결이 끊겼습니다. 다시 시도해 주세요.';
    } finally {
        if (!pending.dataset.sent) pending.remove();
        updateWelcome();
        send.disabled = reset.disabled = false;
        input.disabled = false;
        input.focus({preventScroll: true});
    }
});
