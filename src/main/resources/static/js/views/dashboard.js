/* Início do cliente: visão geral do plano e atalhos. */
import { api } from '../api.js';
import { esc, fmtDateTime } from '../ui.js';

export async function render(root, { me }) {
  const [tabs, indicators] = await Promise.all([
    api.get(`/api/clients/${me.id}/tabs`),
    api.get('/api/chat/indicators?days=90'),
  ]);
  const firstName = (me.name || '').split(' ')[0];
  const updated = tabs.map((t) => t.contentUpdatedAt).filter(Boolean).sort().pop();
  const top = indicators.topSources.length ? indicators.topSources[0].label : '—';
  const subtitle = tabs.length
    ? 'Seu Plano de Negócios está disponível para consulta.'
    : 'Seu plano está sendo preparado pela consultoria. Assim que as primeiras etapas forem publicadas, elas aparecem aqui.';
  const rows = [
    ['Última atualização', updated ? fmtDateTime(updated) : 'Ainda não publicado'],
    ['Etapas disponíveis', String(tabs.length)],
    ['Consultas realizadas', String(indicators.total)],
    ['Tema mais consultado', top],
    ['Perguntas sem resposta no plano', String(indicators.unanswered)],
  ];
  const links = [
    ['plano', 'Plano de negócios', 'Leia cada etapa do plano organizada como um documento.'],
    ['chat', 'Consultar plano', 'Faça perguntas e veja de qual seção veio a resposta.'],
    ['indicadores', 'Indicadores', 'Veja o que mais é consultado e onde o plano pode ficar mais claro.'],
  ];
  root.innerHTML = `
    <div class="section-head"><h1>Olá, ${esc(firstName)}</h1><p>${esc(subtitle)}</p></div>
    <section class="block" aria-labelledby="ov-title">
      <h2 class="sec-title" id="ov-title">Visão geral</h2>
      <p class="sec-sub">Consulte as principais informações do seu Plano de Negócios.</p>
      <dl class="kv-list">${rows.map((r) => `<div class="kv"><dt>${esc(r[0])}</dt><dd>${esc(r[1])}</dd></div>`).join('')}</dl>
    </section>
    <section class="block" aria-labelledby="ex-title">
      <h2 class="sec-title" id="ex-title">Explore seu plano</h2>
      <div class="link-list">${links.map((l) => `
        <a class="link-row" href="#/${l[0]}"><span><b>${esc(l[1])}</b><span>${esc(l[2])}</span></span><span class="arrow" aria-hidden="true">›</span></a>`).join('')}
      </div>
    </section>`;
}
