/* Consultar plano: conversas com o assistente, que responde só a partir do plano e indica a fonte. */
import { api } from '../api.js';
import { esc, initials, onChange, onClick, onInput, onSubmit, confirmDialog, toast } from '../ui.js';

let S = null;

const FALLBACK_SUGGESTIONS = [
  'Qual é o nosso público-alvo?', 'Quais são os nossos principais riscos?',
  'Qual é o ponto de equilíbrio?', 'Quem são os nossos principais concorrentes?',
];

function suggestionsFrom(tabs) {
  const own = [];
  tabs.forEach((t) => (t.suggestedQuestions || []).forEach((q) => { if (!own.includes(q)) own.push(q); }));
  return (own.length >= 2 ? own : FALLBACK_SUGGESTIONS).slice(0, 4);
}

function normalize(m) {
  return {
    role: m.role === 'USER' ? 'user' : 'ai', html: m.html, source: m.source || null,
    inference: !!m.inference, degraded: !!m.degraded,
  };
}

export async function render(root, { me, arg }) {
  const [convs, tabs] = await Promise.all([api.get('/api/chat/conversations'), api.get(`/api/clients/${me.id}/tabs`)]);
  S = { root, me, convs, currentId: null, messages: [], filter: '', sending: false,
        suggestions: suggestionsFrom(tabs), hasContent: tabs.length > 0 };
  root.innerHTML = `
    <div class="chat-shell">
      <div class="conv-col">
        <button class="new-conv-btn" type="button" data-action="chat-new">
          <svg width="15" height="15" viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M10 4v12M4 10h12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
          Nova conversa
        </button>
        <div class="conv-search"><input type="text" placeholder="Pesquisar conversas..." aria-label="Pesquisar conversas" data-input="chat-filter"></div>
        <div class="conv-list scrollbar-thin" id="conv-list" role="list"></div>
      </div>
      <div class="chat-col">
        <div class="chat-head"><h1>Consultar plano</h1><p>Faça uma pergunta sobre seu Plano de Negócios.</p>
          <div class="mobile-conv"><select id="conv-pick" aria-label="Conversas anteriores" data-change="chat-pick"></select>
            <button class="btn small" type="button" data-action="chat-new">Nova conversa</button></div></div>
        <div class="chat-thread scrollbar-thin" id="chat-thread" role="log" aria-live="polite" aria-label="Conversa"></div>
        <form class="chat-input-wrap" data-form="chat-send" novalidate>
          <div class="chat-input-box">
            <textarea id="chat-input" rows="1" maxlength="500" aria-label="Sua pergunta sobre o Plano de Negócios" placeholder="Pergunte qualquer coisa sobre seu Plano de Negócios..."></textarea>
            <button class="send-btn" id="send-btn" type="submit" aria-label="Enviar">
              <svg width="16" height="16" viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M3 10h13M11 5l5 5-5 5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </button>
          </div>
          <div class="chat-input-hint">Enter envia · Shift + Enter quebra a linha</div>
        </form>
      </div>
    </div>`;
  const input = root.querySelector('#chat-input');
  input.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); submit(); }
  });
  input.addEventListener('input', () => grow(input));
  paintConvs();
  paintThread();
  if (arg && S.convs.some((c) => String(c.id) === arg)) await openConversation(Number(arg));
  return () => { S = null; };
}

function grow(el) { el.style.height = 'auto'; el.style.height = Math.min(el.scrollHeight, 130) + 'px'; }

function paintConvs() {
  if (!S) return;
  const list = document.getElementById('conv-list');
  if (!list) return;
  const shown = S.convs.filter((c) => !S.filter || c.title.toLowerCase().includes(S.filter));
  const pick = document.getElementById('conv-pick');
  if (pick) {
    pick.innerHTML = '<option value="">Conversas anteriores</option>' + S.convs.map((c) =>
      `<option value="${c.id}"${c.id === S.currentId ? ' selected' : ''}>${esc(c.title)}</option>`).join('');
  }
  list.innerHTML = shown.length ? shown.map((c) => `
    <div class="conv-item${c.id === S.currentId ? ' active' : ''}" role="listitem">
      <button class="conv-open" type="button" data-action="chat-open" data-id="${c.id}"${c.id === S.currentId ? ' aria-current="true"' : ''}>
        <span class="dot" aria-hidden="true"></span><span class="t">${esc(c.title)}</span>
      </button>
      <button class="kebab" type="button" title="Excluir conversa" aria-label="Excluir conversa ${esc(c.title)}" data-action="chat-delete" data-id="${c.id}">
        <svg width="14" height="14" viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M5 6h10M8.5 6V4.5h3V6M6 6l.6 9.5h6.8L14 6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </button>
    </div>`).join('') : '<p class="empty-note" style="padding:8px 10px;margin:0;">Nenhuma conversa ainda.</p>';
}

function messageHtml(m) {
  const avatar = m.role === 'ai' ? '<img src="img/logo-empresa-jr.png" alt="Chat Jr" width="28" height="28">' : esc(initials(S.me.name));
  const tag = m.inference ? '<div class="msg-infer-tag">Inferência</div>' : '';
  const source = m.source ? `<div class="msg-source"><span aria-hidden="true">📄</span> <span><b>Fonte no plano:</b> ${esc(m.source)}</span></div>` : '';
  const note = m.degraded ? '<div class="msg-note">O assistente de IA está indisponível agora. Esta resposta veio de uma busca direta no texto do plano.</div>' : '';
  // O HTML das mensagens já vem sanitizado pelo servidor (somente tags permitidas, sem atributos).
  return `<div class="msg-row ${m.role}"><div class="msg-avatar">${avatar}</div><div class="msg-bubble">${tag}${m.html}${source}${note}</div></div>`;
}

function paintThread() {
  if (!S) return;
  const thread = document.getElementById('chat-thread');
  if (!thread) return;
  if (!S.messages.length) {
    const intro = S.hasContent
      ? 'As respostas vêm somente do conteúdo do plano e indicam a seção usada. Quando a informação não está no plano, o chat avisa em vez de inventar.'
      : 'A consultoria ainda não publicou etapas do seu plano. Quando houver conteúdo liberado, você poderá perguntar aqui.';
    thread.innerHTML = `
      <div class="chat-empty">
        <h2>Faça uma pergunta sobre o plano da ${esc(S.me.company || 'sua empresa')}</h2>
        <p>${esc(intro)}</p>
        ${S.hasContent ? `<div class="suggest-grid">${S.suggestions.map((q) => `
          <button class="suggest-chip" type="button" data-action="chat-suggest" data-q="${esc(q)}"><span>${esc(q)}</span><span class="arrow" aria-hidden="true">›</span></button>`).join('')}</div>` : ''}
      </div>`;
    return;
  }
  thread.innerHTML = S.messages.map(messageHtml).join('');
  thread.scrollTop = thread.scrollHeight;
}

async function openConversation(id) {
  const mine = S;
  try {
    const loaded = (await api.get(`/api/chat/conversations/${id}/messages`)).map(normalize);
    if (S !== mine) return;
    S.messages = loaded;
    S.currentId = id;
    paintConvs();
    paintThread();
  } catch (error) { toast(error.message, 'error'); }
}

function newConversation() {
  if (!S) return;
  S.currentId = null;
  S.messages = [];
  paintConvs();
  paintThread();
  document.getElementById('chat-input').focus();
}

async function submit(preset) {
  if (!S || S.sending) return;
  const mine = S;
  const input = document.getElementById('chat-input');
  const text = (preset !== undefined ? preset : input.value).trim();
  if (!text) return;
  S.sending = true;
  input.value = '';
  grow(input);
  document.getElementById('send-btn').disabled = true;
  S.messages.push({ role: 'user', html: esc(text) });
  paintThread();
  const thread = document.getElementById('chat-thread');
  thread.insertAdjacentHTML('beforeend',
    '<div class="msg-row ai" id="typing-row"><div class="msg-avatar"><img src="img/logo-empresa-jr.png" alt="" width="28" height="28"></div>' +
    '<div class="msg-bubble"><div class="typing-dots" role="status" aria-label="O assistente está respondendo"><span></span><span></span><span></span></div></div></div>');
  thread.scrollTop = thread.scrollHeight;
  try {
    const answer = await api.post('/api/chat', { conversationId: mine.currentId, question: text });
    if (S !== mine) return;
    mine.currentId = answer.conversationId;
    mine.messages.push({ role: 'ai', html: answer.html, source: answer.source, inference: answer.inference, degraded: answer.degraded });
    mine.convs = await api.get('/api/chat/conversations');
    if (S !== mine) return;
    paintConvs();
    paintThread();
  } catch (error) {
    if (S !== mine) return;
    mine.messages.pop();
    paintThread();
    input.value = text;
    grow(input);
    const box = document.getElementById('chat-thread');
    box.insertAdjacentHTML('beforeend', `<div class="form-error thread-error" role="alert">${esc(error.message)}</div>`);
    box.scrollTop = box.scrollHeight;
  } finally {
    mine.sending = false;
    const send = document.getElementById('send-btn');
    if (send) send.disabled = false;
    const field = document.getElementById('chat-input');
    if (field) field.focus();
  }
}

onSubmit('chat-send', () => submit());
onClick('chat-new', newConversation);
onChange('chat-pick', (el) => { if (el.value) openConversation(Number(el.value)); else newConversation(); });
onClick('chat-open', (el) => openConversation(Number(el.dataset.id)));
onClick('chat-suggest', (el) => submit(el.dataset.q));
onInput('chat-filter', (el) => { if (!S) return; S.filter = el.value.toLowerCase(); paintConvs(); });
onClick('chat-delete', async (el) => {
  const id = Number(el.dataset.id);
  if (!S) return;
  const conv = S.convs.find((c) => c.id === id);
  const ok = await confirmDialog({ title: 'Excluir conversa', message: `Excluir a conversa "${conv ? conv.title : ''}"? As mensagens dela serão apagadas.`, confirmLabel: 'Excluir', danger: true });
  if (!ok || !S) return;
  try {
    await api.del(`/api/chat/conversations/${id}`);
    if (!S) return;
    S.convs = S.convs.filter((c) => c.id !== id);
    if (S.currentId === id) { S.currentId = null; S.messages = []; }
    paintConvs();
    paintThread();
    toast('Conversa excluída.', 'ok');
  } catch (error) { toast(error.message, 'error'); }
});
