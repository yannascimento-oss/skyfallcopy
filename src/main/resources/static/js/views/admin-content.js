/* Conteúdo dos planos: etapas de cada cliente, PDF, processamento pela IA, publicação, escopo e versões. */
import { api } from '../api.js';
import {
  esc, fmtBytes, fmtDateTime, onChange, onClick, onInput, onSubmit, openModal, confirmDialog, toast, withBusy,
} from '../ui.js';

let S = null;
let timer = null;

function stopPolling() { if (timer) { clearInterval(timer); timer = null; } }

export async function render(root, { arg }) {
  S = { root, clients: [], clientId: null, tabs: [], tabId: null, tab: null, att: null, versions: [], dirty: false };
  S.clients = await api.get('/api/admin/clients');
  if (!S.clients.length) {
    root.innerHTML = `<div class="section-head"><h1>Conteúdo dos planos</h1><p>Gerencie as etapas de cada plano.</p></div>
      <div class="card" style="padding:26px;"><p class="empty-note" style="margin:0;">Cadastre um cliente em "Clientes &amp; Planos" para começar a montar o plano dele.</p></div>`;
    return undefined;
  }
  S.clientId = arg && S.clients.some((c) => String(c.id) === arg) ? Number(arg) : S.clients[0].id;
  root.innerHTML = `
    <div class="section-head"><h1>Conteúdo dos planos</h1>
      <p>Gerencie as etapas de cada plano: material, processamento, publicação e quem pode ver.</p></div>
    <div class="field ct-client-pick"><label for="ct-client">Cliente</label>
      <select id="ct-client" data-change="ct-client">${S.clients.map((c) =>
        `<option value="${c.id}"${c.id === S.clientId ? ' selected' : ''}>${esc(c.company || c.name)}</option>`).join('')}</select></div>
    <div class="ct-summary" id="ct-summary"></div>
    <div class="ct-layout"><div class="ct-list" id="ct-list"></div><div class="ct-detail" id="ct-detail"></div></div>`;
  await loadClient();
  return () => { stopPolling(); S = null; };
}

const base = () => `/api/admin/clients/${S.clientId}/tabs`;

async function loadClient(keepTab) {
  const mine = S;
  const tabs = await api.get(`/api/clients/${S.clientId}/tabs`);
  if (S !== mine) return;
  S.tabs = tabs;
  const wanted = keepTab && S.tabs.some((t) => t.id === keepTab) ? keepTab : (S.tabs[0] && S.tabs[0].id);
  paintList();
  if (wanted) await selectTab(wanted, true); else document.getElementById('ct-detail').innerHTML = '';
}

function tabStatus(t) {
  if (t.allowed === false) return '<span class="pill amber">Bloqueada</span>';
  if (t.published) return '<span class="pill teal">Publicada</span>';
  return t.contentVersion > 0 ? '<span class="pill gray">Rascunho</span>' : '<span class="pill gray">Vazia</span>';
}

function paintList() {
  if (!S || !document.getElementById('ct-list')) return;
  const published = S.tabs.filter((t) => t.published && t.allowed !== false).length;
  document.getElementById('ct-summary').innerHTML =
    `<span class="pill blue">${S.tabs.length} etapas</span> <span class="pill teal">${published} visíveis ao cliente</span>`;
  document.getElementById('ct-list').innerHTML = `
    <div class="row-flex" style="padding:0 0 10px;"><b class="grow">Etapas</b><button class="btn blue small" type="button" data-action="ct-new-tab">+ Nova etapa</button></div>
    ${S.tabs.map((t) => `<button type="button" class="ct-row${t.id === S.tabId ? ' active' : ''}" data-action="ct-select" data-id="${t.id}"${t.id === S.tabId ? ' aria-current="true"' : ''}>
      <span class="grow">${esc(t.name)}</span>${tabStatus(t)}</button>`).join('')}`;
}

async function selectTab(id, force) {
  if (!S) return;
  if (!force && S.dirty && !(await confirmDialog({ title: 'Descartar alterações?', message: 'Você alterou esta etapa e ainda não salvou.', confirmLabel: 'Descartar', danger: true }))) return;
  if (!S) return;
  const mine = S;
  stopPolling();
  S.tabId = id;
  S.dirty = false;
  paintList();
  const box = document.getElementById('ct-detail');
  box.innerHTML = '<p class="skeleton">Carregando…</p>';
  try {
    const [tab, att, versions] = await Promise.all([
      api.get(`/api/clients/${S.clientId}/tabs/${id}`), api.get(`${base()}/${id}/attachment`), api.get(`${base()}/${id}/versions`)]);
    if (S !== mine || S.tabId !== id) return;
    S.tab = tab; S.att = att; S.versions = versions;
    paintDetail();
    if (att.state === 'PROCESSING') startPolling();
  } catch (error) { if (S === mine) box.innerHTML = `<div class="form-error" role="alert">${esc(error.message)}</div>`; }
}

function lines(list) { return (list || []).join('\n'); }
function toLines(text) { return text.split('\n').map((l) => l.trim()).filter(Boolean); }

function attachmentHtml() {
  const a = S.att;
  const pill = { IDLE: a.hasPdf ? '<span class="pill amber">Aguardando processamento</span>' : '<span class="pill gray">Sem PDF</span>',
    PROCESSING: '<span class="pill blue">Processando…</span>', DONE: '<span class="pill teal">Processada</span>', FAILED: '<span class="pill red">Falhou</span>' }[a.state];
  const busy = a.state === 'PROCESSING';
  let status = '';
  if (busy) status = '<div class="stat-line" role="status">○ Lendo o PDF e montando o conteúdo. Isso leva alguns segundos.</div>';
  else if (a.state === 'FAILED') status = `<div class="stat-line err" role="alert">✕ ${esc(a.message || 'O processamento falhou.')}</div>`;
  else if (a.state === 'DONE') status = `<div class="stat-line ok">✓ Processada em ${esc(fmtDateTime(a.processedAt))} · ${a.extractedSections} seção(ões) encontrada(s)</div>${a.message ? `<div class="extract-toast">${esc(a.message)}</div>` : ''}`;
  else status = `<div class="stat-line">○ ${a.hasPdf ? 'PDF anexado, aguardando processamento.' : 'Anexe um PDF para poder processar.'}</div>`;
  return `
    <div class="aba-tag"><span>Material desta etapa</span><b>${esc(S.tab.name)}</b>${pill}</div>
    <div class="attach-block"><div class="lbl2">Arquivo PDF</div>
      <div class="attach-file-row">
        <svg width="17" height="17" viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M6 2.5h6l3 3V17a.5.5 0 0 1-.5.5h-9A.5.5 0 0 1 5 17V3a.5.5 0 0 1 .5-.5Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>
        <span class="fn${a.hasPdf ? '' : ' empty'}">${a.hasPdf ? esc(a.pdfName) : 'Nenhum PDF anexado'}</span>
        <button class="btn" type="button" data-action="att-pick"${busy ? ' disabled' : ''}>${a.hasPdf ? 'Substituir' : 'Anexar PDF'}</button>
        ${a.hasPdf ? `<button class="btn danger" type="button" data-action="att-remove"${busy ? ' disabled' : ''}>Remover</button>` : ''}
      </div>
      <input type="file" id="att-file" class="file-input-hidden" accept="application/pdf,.pdf" tabindex="-1" aria-label="Escolher arquivo PDF" data-change="att-file">
      ${a.hasPdf ? `<div class="help" style="margin-top:7px;font-size:12px;color:var(--muted);">${a.pdfPages} página(s) · ${a.pdfChars.toLocaleString('pt-BR')} caracteres lidos · ${esc(fmtBytes(a.storedBytes))}. O chat já consulta este PDF.</div>` : ''}
    </div>
    <div class="attach-block"><div class="lbl2">Link complementar (slide, planilha...)</div>
      <form class="attach-link-row" data-form="att-link">
        <input type="text" name="link" aria-label="Link complementar" placeholder="https://docs.google.com/..." value="${esc(a.slideLink || '')}"><button class="btn" type="submit">Salvar</button>
      </form></div>
    <div class="attach-status">${status}
      <div class="attach-actions"><button class="btn yellow" type="button" data-action="att-process"${a.hasPdf && !busy ? '' : ' disabled'} style="width:100%;">${a.state === 'DONE' ? 'Reprocessar' : 'Processar com IA'}</button></div>
    </div>`;
}

function paintAttachment() {
  if (!S) return;
  const panel = document.getElementById('att-panel');
  if (panel) panel.innerHTML = attachmentHtml();
}

function paintDetail() {
  const box = S && document.getElementById('ct-detail');
  if (!box) return;
  const t = S.tab;
  box.innerHTML = `
    <div>
      <div class="ct-detail-head"><h2>${esc(t.name)}</h2>
        <div class="row-flex">${tabStatus(t)}
          <button class="btn small${t.published ? '' : ' blue'}" type="button" data-action="ct-publish">${t.published ? 'Despublicar' : 'Salvar e publicar'}</button></div></div>
      <div class="toggle-row"><div><div class="t" id="scope-label">Liberada para este cliente</div><div class="d2">Desligue para esconder a etapa do cliente sem despublicá-la.</div></div>
        <button class="switch light${t.allowed !== false ? ' on' : ''}" type="button" role="switch" aria-checked="${t.allowed !== false}" aria-labelledby="scope-label" data-action="ct-scope"></button></div>
      <form data-form="ct-save" id="ct-form" novalidate style="margin-top:14px;">
        <div id="ct-error" class="form-error hidden" role="alert"></div>
        <div class="field"><label for="f-title">Título</label><input id="f-title" name="title" type="text" value="${esc(t.title)}" data-input="ct-dirty"></div>
        <div class="field"><label for="f-short">Descrição curta</label><input id="f-short" name="shortDescription" type="text" value="${esc(t.shortDescription || '')}" data-input="ct-dirty"></div>
        <div class="field"><label for="f-what">O que é esta etapa</label><textarea id="f-what" name="whatIsIt" rows="2" data-input="ct-dirty">${esc(t.whatIsIt || '')}</textarea></div>
        <div class="field"><label for="f-obj">Objetivo</label><textarea id="f-obj" name="objective" rows="2" data-input="ct-dirty">${esc(t.objective || '')}</textarea></div>
        <div class="field"><label for="f-points">Principais pontos (um por linha, até 8)</label><textarea id="f-points" name="keyPoints" rows="4" data-input="ct-dirty">${esc(lines(t.keyPoints))}</textarea></div>
        <div class="field"><label for="f-questions">Perguntas sugeridas no chat (uma por linha, até 6)</label><textarea id="f-questions" name="suggestedQuestions" rows="3" data-input="ct-dirty">${esc(lines(t.suggestedQuestions))}</textarea></div>
        <div class="field"><label for="f-html">Conteúdo da etapa</label><textarea id="f-html" class="code" name="html" rows="9" data-input="ct-dirty" aria-describedby="f-html-help">${esc(t.html || '')}</textarea>
          <div class="help" id="f-html-help">Tags aceitas: p, b, strong, ul, ol, li, h4, table, thead, tbody, tr, th, td, br. O resto é removido ao salvar. O processamento do PDF preenche isto automaticamente.</div></div>
        <div class="row-flex"><button class="btn blue" type="submit" id="ct-save-btn" disabled>Salvar alterações</button>
          <span class="muted" style="font-size:12.5px;">${t.contentVersion ? `Versão ${t.contentVersion} · atualizada em ${esc(fmtDateTime(t.contentUpdatedAt))}` : 'Ainda sem conteúdo.'}${t.source ? ` · fonte: ${esc(t.source)}` : ''}</span></div>
      </form>
      ${t.html ? `<h3 class="sec-title" style="margin-top:22px;">Como o cliente vê (último salvo)</h3><div class="ct-preview"><div class="body-text">${t.html}</div></div>` : ''}
    </div>
    <div class="attach-panel ct-block" id="att-panel">${attachmentHtml()}</div>
    <div class="ct-block">
      <h3 class="sec-title">Versões anteriores</h3>
      <p class="sec-sub">Cada alteração de conteúdo guarda o estado anterior. Restaurar também guarda o estado atual, então dá para desfazer.</p>
      ${S.versions.length ? S.versions.map((v) => `<div class="version-row"><span class="grow">Versão ${v.version} · ${esc({ EDIT: 'edição', PROCESS: 'processamento', RESTORE: 'restauração' }[v.reason] || v.reason)} · ${esc(fmtDateTime(v.createdAt))}${v.createdBy ? ' · ' + esc(v.createdBy) : ''}</span>
        <button class="btn small" type="button" data-action="ct-restore" data-version="${v.version}" aria-label="Restaurar a versão ${v.version}">Restaurar</button></div>`).join('')
        : '<p class="empty-note" style="margin:0;">Nenhuma versão anterior ainda.</p>'}
    </div>
    <div class="row-flex ct-block"><button class="btn danger" type="button" data-action="ct-delete">Excluir esta etapa</button></div>`;
}

function formBody(published) {
  const f = document.getElementById('ct-form');
  return { title: f.title.value.trim(), html: f.html.value, shortDescription: f.shortDescription.value.trim() || null,
    whatIsIt: f.whatIsIt.value.trim() || null, objective: f.objective.value.trim() || null,
    keyPoints: toLines(f.keyPoints.value), suggestedQuestions: toLines(f.suggestedQuestions.value),
    source: S.tab.source || null, published };
}

async function saveForm(published, button, busyLabel) {
  const err = document.getElementById('ct-error');
  err.classList.add('hidden');
  const mine = S;
  const body = formBody(published);
  if (!body.title) { err.textContent = 'Informe o título da etapa.'; err.classList.remove('hidden'); return false; }
  return withBusy(button, busyLabel, async () => {
    try {
      const saved = await api.put(`${base()}/${S.tabId}`, body);
      const versions = await api.get(`${base()}/${S.tabId}/versions`);
      const tabs = await api.get(`/api/clients/${S.clientId}/tabs`);
      if (S !== mine) return true;
      S.tab = saved; S.dirty = false; S.versions = versions; S.tabs = tabs;
      paintList(); paintDetail();
      return true;
    } catch (error) { err.textContent = error.message; err.classList.remove('hidden'); return false; }
  });
}

function startPolling() {
  stopPolling();
  const mine = S;
  const id = S.tabId;
  timer = setInterval(async () => {
    if (S !== mine) { stopPolling(); return; }
    try {
      const att = await api.get(`${base()}/${id}/attachment`);
      if (S !== mine || S.tabId !== id) { stopPolling(); return; }
      S.att = att;
      if (att.state === 'PROCESSING') { paintAttachment(); return; }
      stopPolling();
      S.tabs = await api.get(`/api/clients/${S.clientId}/tabs`);
      await selectTab(id, true);
      if (att.state === 'DONE') toast('Etapa processada.', 'ok'); else toast(att.message || 'O processamento falhou.', 'error');
    } catch (error) { stopPolling(); toast(error.message, 'error'); }
  }, 1200);
}

onChange('ct-client', async (el) => {
  if (S.dirty && !(await confirmDialog({ title: 'Descartar alterações?', message: 'Você alterou uma etapa e ainda não salvou.', confirmLabel: 'Descartar', danger: true }))) {
    el.value = String(S.clientId); return;
  }
  S.clientId = Number(el.value); S.tabId = null; S.dirty = false;
  history.replaceState(null, '', `#/conteudo/${S.clientId}`);
  stopPolling();
  await loadClient();
});
onClick('ct-select', (el) => selectTab(Number(el.dataset.id)));
onInput('ct-dirty', () => { S.dirty = true; const b = document.getElementById('ct-save-btn'); if (b) b.disabled = false; });
onSubmit('ct-save', (form) => saveForm(S.tab.published, form.querySelector('#ct-save-btn'), 'Salvando…').then((ok) => { if (ok) toast('Etapa salva.', 'ok'); }));
onClick('ct-publish', async (el) => {
  const publish = !S.tab.published;
  const ok = await saveForm(publish, el, 'Salvando…');
  if (ok) toast(publish ? 'Etapa publicada: o cliente já pode vê-la.' : 'Etapa despublicada.', 'ok');
});
onClick('ct-scope', async (el) => {
  const allow = el.getAttribute('aria-checked') !== 'true';
  try {
    await api.put(`${base()}/${S.tabId}/scope`, { allowed: allow });
    S.tabs = await api.get(`/api/clients/${S.clientId}/tabs`);
    S.tab = { ...S.tab, allowed: allow };
    paintList();
    el.classList.toggle('on', allow); el.setAttribute('aria-checked', String(allow));
    toast(allow ? 'Etapa liberada para o cliente.' : 'Etapa bloqueada para o cliente.', 'ok');
  } catch (error) { toast(error.message, 'error'); }
});
onClick('ct-new-tab', async () => {
  const created = await openModal({
    title: 'Nova etapa', description: 'Crie uma etapa extra no plano deste cliente.',
    body: '<div class="field"><label for="nt-name">Nome da etapa</label><input type="text" id="nt-name" autocomplete="off" placeholder="Ex: Análise Competitiva"></div>',
    actions: [{ id: 'cancel', label: 'Cancelar' }, { id: 'save', label: 'Criar etapa', kind: 'blue', primary: true, handler: async (m) => {
      const name = m.$('#nt-name').value.trim();
      if (!name) { m.error('Informe o nome da etapa.'); return; }
      m.busy(true);
      try { m.close(await api.post(base(), { name })); } catch (e) { m.busy(false); m.error(e.message); }
    } }],
  });
  if (created) { S.dirty = false; await loadClient(created.id); toast('Etapa criada.', 'ok'); }
});
onClick('ct-delete', async () => {
  const ok = await confirmDialog({ title: 'Excluir etapa', message: `Excluir "${S.tab.name}"? O conteúdo, o PDF e as versões dela serão perdidos.`, confirmLabel: 'Excluir', danger: true });
  if (!ok) return;
  try { await api.del(`${base()}/${S.tabId}`); S.dirty = false; S.tabId = null; await loadClient(); toast('Etapa excluída.', 'ok'); }
  catch (error) { toast(error.message, 'error'); }
});
onClick('ct-restore', async (el) => {
  const version = Number(el.dataset.version);
  const ok = await confirmDialog({ title: 'Restaurar versão', message: `Voltar esta etapa ao estado da versão ${version}? O estado atual fica guardado.`, confirmLabel: 'Restaurar' });
  if (!ok) return;
  try { await api.post(`${base()}/${S.tabId}/versions/${version}/restore`, {}); S.dirty = false; await loadClient(S.tabId); toast('Versão restaurada.', 'ok'); }
  catch (error) { toast(error.message, 'error'); }
});

onClick('att-pick', () => { const input = document.getElementById('att-file'); if (input) { input.value = ''; input.click(); } });
onChange('att-file', async (el) => {
  const file = el.files && el.files[0];
  if (!file) return;
  const pick = document.querySelector('[data-action="att-pick"]');
  if (pick) { pick.disabled = true; pick.textContent = 'Enviando…'; }
  try {
    S.att = await api.upload(`${base()}/${S.tabId}/attachment`, file);
    paintAttachment();
    toast('PDF anexado. Agora é só processar.', 'ok');
  } catch (error) { paintAttachment(); toast(error.message, 'error'); }
});
onClick('att-remove', async () => {
  const ok = await confirmDialog({ title: 'Remover PDF', message: 'O arquivo e o texto lido dele serão apagados e o chat deixa de consultá-lo. O conteúdo já escrito na etapa permanece.', confirmLabel: 'Remover', danger: true });
  if (!ok) return;
  try { await api.del(`${base()}/${S.tabId}/attachment`); S.att = await api.get(`${base()}/${S.tabId}/attachment`); paintAttachment(); toast('PDF removido.', 'ok'); }
  catch (error) { toast(error.message, 'error'); }
});
onSubmit('att-link', async (form) => {
  try { S.att = await api.put(`${base()}/${S.tabId}/attachment/link`, { slideLink: form.link.value.trim() }); paintAttachment(); toast('Link salvo.', 'ok'); }
  catch (error) { toast(error.message, 'error'); }
});
onClick('att-process', async (el) => {
  await withBusy(el, 'Iniciando…', async () => {
    try { S.att = await api.post(`${base()}/${S.tabId}/attachment/process`, {}); paintAttachment(); startPolling(); }
    catch (error) { toast(error.message, 'error'); }
  });
});
