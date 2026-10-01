/* Indicadores calculados das perguntas reais. O cliente vê os próprios; a consultoria vê o geral ou o de um cliente. */
import { api } from '../api.js';
import { esc, pct, fmtDateTime, onChange } from '../ui.js';

let S = null;

const TYPE_LABELS = { INFORMACAO: 'Informação', DUVIDA: 'Dúvida', INTERPRETACAO: 'Interpretação', DECISAO: 'Decisão' };

function bars(items, total, emptyText) {
  if (!items.length) return `<p class="empty-note" style="margin:0;">${esc(emptyText)}</p>`;
  return items.map((i) => {
    const p = pct(i.count, total);
    return `<div class="bar-row"><div class="lbl">${esc(i.label)}</div>
      <div class="bar-track" role="img" aria-label="${esc(i.label)}: ${p}%"><div class="bar-fill" data-w="${p}"></div></div>
      <div class="val">${p}%</div></div>`;
  }).join('');
}

export async function render(root, { me, arg }) {
  S = { root, me, days: 30, clientId: me.role === 'ADMIN' && arg ? arg : '', clients: [] };
  if (me.role === 'ADMIN') S.clients = await api.get('/api/admin/clients');
  const header = `
    <div class="section-head"><h1>Indicadores</h1>
      <p>${me.role === 'ADMIN' ? 'O que os clientes estão tentando entender nos planos, e onde o conteúdo ainda pode ficar mais claro.' : 'O que você mais consulta no plano, e onde ele ainda pode ficar mais claro.'}</p></div>
    <div class="row-flex" style="margin-bottom:18px;">
      ${me.role === 'ADMIN' ? `<div class="field" style="margin:0;min-width:240px;"><label for="ind-client">Cliente</label>
        <select id="ind-client" data-change="ind-client"><option value="">Todos os clientes</option>
        ${S.clients.map((c) => `<option value="${c.id}"${String(c.id) === String(S.clientId) ? ' selected' : ''}>${esc(c.company || c.name)}</option>`).join('')}</select></div>` : ''}
      <div class="field" style="margin:0;"><label for="ind-days">Período</label>
        <select id="ind-days" data-change="ind-days"><option value="7">Últimos 7 dias</option><option value="30" selected>Últimos 30 dias</option><option value="90">Últimos 90 dias</option></select></div>
    </div>
    <div id="ind-body"></div>`;
  root.innerHTML = header;
  await paint();
  return () => { S = null; };
}

function url() {
  if (S.me.role !== 'ADMIN') return `/api/chat/indicators?days=${S.days}`;
  return S.clientId ? `/api/admin/clients/${S.clientId}/indicators?days=${S.days}` : `/api/admin/indicators?days=${S.days}`;
}

async function paint() {
  if (!S) return;
  const mine = S;
  const body = document.getElementById('ind-body');
  if (!body) return;
  body.innerHTML = '<p class="skeleton">Carregando…</p>';
  let d;
  try { d = await api.get(url()); } catch (error) {
    if (S !== mine) return;
    body.innerHTML = `<div class="form-error" role="alert">${esc(error.message)}</div>`; return;
  }
  if (S !== mine) return;
  const answeredSources = d.topSources.reduce((n, s) => n + s.count, 0);
  const typeTotal = Object.values(d.byType).reduce((a, b) => a + b, 0);
  const rows = [
    ['Consultas realizadas', String(d.total)],
    ['Respostas encontradas no plano', d.total ? `${d.answered} de ${d.total} (${d.answeredPercent}%)` : '—'],
    ['Perguntas sem resposta no plano', d.total ? `${d.unanswered} (${pct(d.unanswered, d.total)}%)` : '—'],
    ['Respostas por busca direta (sem IA)', String(d.degraded)],
    ['Última consulta', d.lastQuestionAt ? fmtDateTime(d.lastQuestionAt) : '—'],
  ];
  const peak = Math.max(1, ...d.perDay.map((x) => x.count));
  body.innerHTML = `
    <section class="block" aria-labelledby="i1"><h2 class="sec-title" id="i1">Resumo</h2>
      <dl class="kv-list">${rows.map((r) => `<div class="kv"><dt>${esc(r[0])}</dt><dd>${esc(r[1])}</dd></div>`).join('')}</dl></section>
    <section class="block" aria-labelledby="i2"><h2 class="sec-title" id="i2">Consultas por dia</h2>
      <p class="sec-sub">Perguntas feitas nos últimos ${d.perDay.length} dias.</p>
      <div class="day-bars" role="img" aria-label="Perguntas por dia: ${d.perDay.map((x) => x.count).join(', ')}">${d.perDay.map((x) =>
        `<div class="col"><div class="bar" data-h="${Math.round((x.count / peak) * 100)}" title="${esc(x.date)}: ${x.count}"></div><div class="d">${esc(x.date.slice(8, 10))}</div></div>`).join('')}</div></section>
    <section class="block" aria-labelledby="i3"><h2 class="sec-title" id="i3">Etapas mais consultadas</h2>
      <p class="sec-sub">Participação de cada etapa nas respostas encontradas no plano.</p>
      ${bars(d.topSources, answeredSources, 'Ainda não há consultas registradas. As etapas mais perguntadas aparecem aqui.')}</section>
    <section class="block" aria-labelledby="i4"><h2 class="sec-title" id="i4">Perguntas mais frequentes</h2>
      ${d.frequentQuestions.length ? d.frequentQuestions.map((f) => `<div class="faq-item"><span>${esc(f.label)}</span><span class="q-count">${f.count}×</span></div>`).join('')
        : '<p class="empty-note" style="margin:0;">Nenhuma pergunta se repetiu ainda.</p>'}</section>
    <section class="block" aria-labelledby="i5"><h2 class="sec-title" id="i5">Tipos de consulta</h2>
      <p class="sec-sub">Como as perguntas se dividem entre buscar informação, tirar dúvida, pedir interpretação e decidir.</p>
      ${typeTotal ? Object.entries(d.byType).map(([k, n]) => {
        const p = pct(n, typeTotal);
        return `<div class="bar-row"><div class="lbl">${esc(TYPE_LABELS[k] || k)}</div><div class="bar-track" role="img" aria-label="${esc(TYPE_LABELS[k] || k)}: ${p}%"><div class="bar-fill" data-w="${p}"></div></div><div class="val">${p}%</div></div>`;
      }).join('') : '<p class="empty-note" style="margin:0;">Sem consultas para classificar ainda.</p>'}</section>
    <section class="block" aria-labelledby="i6"><h2 class="sec-title" id="i6">Oportunidades de melhoria</h2>
      <p class="sec-sub">${d.unansweredQuestions.length
        ? `${d.unansweredQuestions.length} ${d.unansweredQuestions.length === 1 ? 'pergunta não encontrou' : 'perguntas não encontraram'} informação suficiente no plano. Elas indicam o que vale detalhar nas próximas entregas.`
        : 'Todas as perguntas feitas até agora foram respondidas a partir do plano.'}</p>
      ${d.unansweredQuestions.length ? `<div class="gap-list">${d.unansweredQuestions.slice(0, 10).map((g) =>
        `<div class="gap-item"><span>${esc(g.question)}</span><span class="gap-theme">${esc(fmtDateTime(g.createdAt))}</span></div>`).join('')}</div>` : ''}</section>`;
  // Larguras e alturas dos gráficos vão por CSSOM (a política de segurança não aceita style= no HTML).
  body.querySelectorAll('[data-w]').forEach((el) => { el.style.width = el.dataset.w + '%'; });
  body.querySelectorAll('[data-h]').forEach((el) => { el.style.height = el.dataset.h + '%'; });
}

onChange('ind-days', (el) => { if (!S) return; S.days = Number(el.value); paint(); });
onChange('ind-client', (el) => { if (!S) return; S.clientId = el.value; paint(); });
