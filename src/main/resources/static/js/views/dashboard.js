/* Início do cliente: progresso do plano (rosca), etapas disponíveis, visão geral e atalhos.
   Também serve à consultoria no modo "ver como o cliente". */
import { api } from '../api.js';
import { esc, fmtDateTime, pct } from '../ui.js';
import { subject } from '../main.js';

/** Rosca de progresso em SVG (mesmo desenho do protótipo). */
function donut(percent, label) {
  const r = 34, circ = 2 * Math.PI * r;
  const p = Math.max(0, Math.min(100, percent));
  const off = circ * (1 - p / 100);
  return `<svg viewBox="0 0 84 84" class="donut" role="img" aria-label="${esc(label)}: ${Math.round(p)}%">
    <circle cx="42" cy="42" r="${r}" fill="none" stroke="var(--line)" stroke-width="10"/>
    <circle cx="42" cy="42" r="${r}" fill="none" stroke="var(--jr-blue)" stroke-width="10" stroke-linecap="round"
      stroke-dasharray="${circ.toFixed(1)}" stroke-dashoffset="${off.toFixed(1)}" transform="rotate(-90 42 42)"/>
    <text x="42" y="48" text-anchor="middle" font-size="19" font-weight="900" fill="var(--ink)" font-family="Roboto, sans-serif">${Math.round(p)}%</text>
  </svg>`;
}

export async function render(root) {
  const who = subject();
  const indicatorsUrl = who.viewAs ? `/api/admin/clients/${who.id}/indicators?days=90` : '/api/chat/indicators?days=90';
  const [allTabs, indicators, progress] = await Promise.all([
    api.get(`/api/clients/${who.id}/tabs`),
    api.get(indicatorsUrl),
    api.get(`/api/clients/${who.id}/progress`),
  ]);
  // A consultoria recebe todas as etapas; no modo "ver como o cliente" mostramos só o que o cliente vê.
  const tabs = who.viewAs ? allTabs.filter((t) => t.published && t.allowed !== false) : allTabs;
  const updated = tabs.map((t) => t.contentUpdatedAt).filter(Boolean).sort().pop();
  const top = indicators.topSources.length ? indicators.topSources[0].label : '—';
  const done = pct(progress.available, progress.total);
  const greeting = who.viewAs ? `Painel de ${who.company}` : `Olá, ${(who.name || '').split(' ')[0]}`;
  const subtitle = tabs.length
    ? (who.viewAs ? `Você está vendo o Chat Jr como ${who.company} vê.` : 'Seu Plano de Negócios está disponível para consulta.')
    : 'O plano está sendo preparado pela consultoria. Assim que as primeiras etapas forem publicadas, elas aparecem aqui.';
  const rows = [
    ['Última atualização', updated ? fmtDateTime(updated) : 'Ainda não publicado'],
    ['Consultas realizadas', String(indicators.total)],
    ['Tema mais consultado', top],
    ['Perguntas sem resposta no plano', String(indicators.unanswered)],
  ];
  const links = [
    ['plano', 'Plano de negócios', 'Leia cada etapa do plano organizada como um documento.'],
    ...(who.viewAs ? [] : [['chat', 'Consultar plano', 'Faça perguntas e veja de qual seção veio a resposta.']]),
    ['indicadores', 'Indicadores', 'Veja o que mais é consultado e onde o plano pode ficar mais claro.'],
  ];
  root.innerHTML = `
    <div class="section-head"><h1>${esc(greeting)}</h1><p>${esc(subtitle)}</p></div>
    <section class="card progress-card" aria-labelledby="pg-title">
      ${donut(done, 'Etapas do plano disponíveis')}
      <div>
        <h3 id="pg-title">Progresso do plano</h3>
        <p class="sub"><b>${progress.available} de ${progress.total}</b> etapas já estão disponíveis para consulta.${progress.available < progress.total ? ' As demais aparecem aqui assim que a consultoria publicar.' : ''}</p>
        ${tabs.length ? `<div class="tab-chip-grid">${tabs.map((t) =>
          `<a class="tab-chip ok" href="#/plano/${t.id}"><span class="dot" aria-hidden="true"></span>${esc(t.name)}</a>`).join('')}</div>` : ''}
      </div>
    </section>
    <section class="block" aria-labelledby="ov-title">
      <h2 class="sec-title" id="ov-title">Visão geral</h2>
      <p class="sec-sub">As principais informações do Plano de Negócios.</p>
      <dl class="kv-list">${rows.map((r) => `<div class="kv"><dt>${esc(r[0])}</dt><dd>${esc(r[1])}</dd></div>`).join('')}</dl>
    </section>
    <section class="block" aria-labelledby="ex-title">
      <h2 class="sec-title" id="ex-title">Explore o plano</h2>
      <div class="link-list">${links.map((l) => `
        <a class="link-row" href="#/${l[0]}"><span><b>${esc(l[1])}</b><span>${esc(l[2])}</span></span><span class="arrow" aria-hidden="true">›</span></a>`).join('')}
      </div>
    </section>`;
}
