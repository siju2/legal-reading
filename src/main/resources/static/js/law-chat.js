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
let messageId = 0;
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

function message(label, text, user = false) {
    const card = document.createElement('article');
    card.className = user ? 'law-message law-message-user' : 'law-message';
    const heading = document.createElement('strong');
    heading.textContent = label;
    const body = document.createElement('p');
    body.textContent = text;
    card.append(heading, body);
    messages.append(card);
    updateWelcome();
    return card;
}

reset.addEventListener('click', () => {
    clearMessages();
    conversation = [];
    status.textContent = '';
    input.value = '';
    input.focus();
});

form.addEventListener('submit', async event => {
    event.preventDefault();
    if (send.disabled || !input.value.trim()) return;
    const data = new URLSearchParams(new FormData(form));
    const nextScope = data.get('notebookId') + ':' + data.has('consent');
    if (nextScope !== scope) { conversation = []; clearMessages(); }
    scope = nextScope;
    const question = input.value.trim();
    data.set('question', question);
    data.set('context', conversation.slice(-6).map(turn => turn.role + ': ' + turn.text).join('\n').slice(-12000));
    const pending = message('나', question, true);
    send.disabled = reset.disabled = true;
    input.disabled = true;
    const deepAnalysis = data.get('mode') === 'deep';
    status.textContent = deepAnalysis ? '공식 근거를 대조하며 자세히 검토하고 있습니다.' : '관련 근거를 찾아 핵심을 정리하고 있습니다.';
    try {
        const response = await fetch(form.action + '/chat', {method: 'POST', body: data});
        if (response.redirected) {
            window.location.assign(response.url);
            return;
        }
        if (!response.ok) {
            status.textContent = {
                400: '질문과 사건 노트 동의 여부를 확인해 주세요.',
                429: 'AI가 이미 답변 중입니다. 잠시 후 다시 시도해 주세요.',
                503: '로컬 AI가 연결되지 않았습니다.'
            }[response.status] || '답변을 받지 못했습니다. 다시 시도해 주세요.';
            return;
        }
        const answer = await response.json();

        const card = message('법률 AI · ' + (deepAnalysis ? '깊은 분석' : '빠른 답변'), answer.text);
        const comparing = /비교|차이/.test(question) && answer.sources.length > 1;
        if (comparing) card.classList.add('law-message-comparison');
        if (answer.sources.length) {
            const details = document.createElement('details');
            const summary = document.createElement('summary');
            summary.textContent = '법령·판례 근거 ' + answer.sources.length + '건';
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
                checked.textContent = '자료 확인일 ' + source.checkedAt;
                item.append(link, checked, text);
                list.append(item);
            }
            details.className = 'source-details';
            details.open = comparing;
            list.className = 'source-cards';
            details.append(summary, list);
            card.append(details);
        }
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
