/* Plano de negócios: cada etapa lida como um documento, com busca no texto e exportação. */
import { api } from '../api.js';
import { esc, fmtDateTime, onClick, onInput, stripHtml, debounce } from '../ui.js';

let S = null;

function section(title, inner) { return `<section class="stage-sec"><h3>${esc(title)}</h3>${inner}</section>`; }

export async function render(root, { me, arg }) {
  const tabs = await api.get(`/api/clients/${me.id}/tabs`);
  S = { root, me, tabs, details: new Map(), activeId: null, term: '' };
  const exportLinks = `
    <div class="row-flex">
      <a class="btn" href="/api/clients/${me.id}/export.pdf" download>Baixar PDF</a>
      <a class="btn" href="/api/clients/${me.id}/export.json" download>Baixar JSON</a>
    </div>`;
  if (!tabs.length) {
    root.innerHTML = `<div class="section-head"><h1>Plano de negócios</h1><p>Leia cada etapa do seu plano.</p></div>
      <div class="card" style="padding:26px;"><p class="empty-note" style="margin:0;">Seu plano ainda não tem etapas publicadas. Assim que a consultoria liberar a primeira, ela aparece aqui.</p></div>`;
    return;
  }
  root.innerHTML = `
    <div class="section-head row-flex" style="align-items:flex-end;justify-content:space-between;">
      <div><h1>Plano de negócios</h1><p>Leia cada etapa do seu plano.</p></div>${exportLinks}
    </div>
    <div class="plan-search">
      <svg width="16" height="16" viewBox="0 0 20 20" fill="none" aria-hidden="true"><circle cx="9" cy="9" r="6" stroke="currentColor" stroke-width="1.6"/><path d="m17 17-3.5-3.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
      <input type="search" id="plan-search-input" aria-label="Pesquisar no plano" placeholder="Pesquisar no plano (ex: margem, concorrentes, payback)" data-input="plan-search" autocomplete="off">
    </div>
    <div class="plan-shell">
      <div class="card plan-cat-list" id="plan-cat-list"></div>
      <div id="plan-content-wrap" aria-live="polite"></div>
    </div>`;
  paintList();
  const wanted = arg && tabs.find((t) => String(t.id) === arg);
  await select((wanted || tabs[0]).id);
  return () => { S = null; };
}

function paintList() {
  const list = S && document.getElementById('plan-cat-list');
  if (!list) return;
  list.innerHTML = '<div class="plan-cat-header"><span>ETAPAS</span></div><div>' +
    S.tabs.map((t) => `<button type="button" class="plan-cat-item${t.id === S.activeId ? ' active' : ''}" data-action="plan-select" data-id="${t.id}"${t.id === S.activeId ? ' aria-current="true"' : ''}>
      <span class="cat-name">${esc(t.name)}</span></button>`).join('') + '</div>';
}

async function detail(id) {
  if (!S) throw new Error('Tela encerrada.');
  if (!S.details.has(id)) S.details.set(id, await api.get(`/api/clients/${S.me.id}/tabs/${id}`));
  return S.details.get(id);
}

async function select(id) {
  if (!S) return;
  const mine = S;
  S.activeId = id;
  S.term = '';
  const input = document.getElementById('plan-search-input');
  if (input) input.value = '';
  history.replaceState(null, '', `#/plano/${id}`);
  paintList();
  const wrap = document.getElementById('plan-content-wrap');
  wrap.innerHTML = '<p class="skeleton">Carregando…</p>';
  try {
    const tab = await detail(id);
    if (S !== mine || S.activeId !== id) return;
    const points = tab.keyPoints || [];
    const lead = tab.shortDescription && tab.shortDescription !== tab.whatIsIt ? `<p class="stage-lead">${esc(tab.shortDescription)}</p>` : '';
    wrap.innerHTML = `<article class="card plan-content">
      <div class="eyebrow">Plano de negócios · ${esc(S.me.company)}</div>
      <h2>${esc(tab.title || tab.name)}</h2>
      ${lead}
      ${tab.whatIsIt ? section('O que é esta etapa?', `<p>${esc(tab.whatIsIt)}</p>`) : ''}
      ${tab.objective ? section('Objetivo', `<p>${esc(tab.objective)}</p>`) : ''}
      ${section('Conteúdo', `<div class="body-text">${tab.html || '<p class="empty-note">Esta etapa ainda não tem texto.</p>'}</div>`)}
      ${points.length ? section('Principais pontos', `<ul class="stage-points">${points.map((k) => `<li>${esc(k)}</li>`).join('')}</ul>`) : ''}
      <p class="stage-updated">${tab.source ? `Fonte: ${esc(tab.source)} · ` : ''}Atualizado em ${esc(fmtDateTime(tab.contentUpdatedAt))}</p>
    </article>`;
  } catch (error) {
    if (S === mine) wrap.innerHTML = `<div class="form-error" role="alert">${esc(error.message)}</div>`;
  }
}

async function search(term) {
  if (!S) return;
  const mine = S;
  term = term.trim().toLowerCase();
  S.term = term;
  if (!term) { await select(S.activeId || S.tabs[0].id); return; }
  const wrap = document.getElementById('plan-content-wrap');
  const all = await Promise.all(S.tabs.map((t) => detail(t.id).catch(() => null)));
  if (S !== mine || S.term !== term) return;
  const hits = all.filter(Boolean).filter((t) => (t.name + ' ' + stripHtml(t.html)).toLowerCase().includes(term));
  if (!hits.length) {
    wrap.innerHTML = `<div class="card" style="padding:26px;color:var(--muted);font-size:14px;">Nada encontrado para "<b>${esc(term)}</b>". Tente outra palavra.</div>`;
    return;
  }
  const pattern = new RegExp('(' + term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ')', 'ig');
  wrap.innerHTML = '<div class="plan-search-results">' + hits.map((t) => {
    const plain = stripHtml(t.html);
    const at = plain.toLowerCase().indexOf(term);
    const from = Math.max(0, at - 70);
    const snippet = esc(plain.slice(from, at + term.length + 110).trim()).replace(pattern, '<mark>$1</mark>');
    return `<button type="button" class="card result-item" data-action="plan-select" data-id="${t.id}">
      <div class="sec">${esc(t.name)}</div><div class="snip">…${snippet}…</div></button>`;
  }).join('') + '</div>';
}

onClick('plan-select', (el) => select(Number(el.dataset.id)));
onInput('plan-search', debounce((el) => search(el.value), 200));
