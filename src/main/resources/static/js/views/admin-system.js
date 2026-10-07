/* Sistema: saúde da instalação, uso e custo estimado da IA, erros recentes. */
import { api } from '../api.js';
import { esc, fmtBytes, fmtDateTime, fmtDuration, fmtUsd, onClick, toast, withBusy } from '../ui.js';

function stat(label, value, sub = '') {
  return `<div class="card stat-card"><div class="label">${esc(label)}</div><div class="value">${esc(value)}</div>${sub ? `<div class="sub">${esc(sub)}</div>` : ''}</div>`;
}

export async function render(root) {
  const s = await api.get('/api/admin/system');
  const used = s.disk.attachmentsBytes;
  root.innerHTML = `
    <div class="section-head"><h1>Sistema</h1><p>Estado da instalação, uso da inteligência artificial e erros recentes.</p></div>
    <section class="block" aria-labelledby="sy1"><h2 class="sec-title" id="sy1">Em números</h2>
      <div class="stat-grid">
        ${stat('Clientes', s.counts.clients)}${stat('Administradores', s.counts.admins)}${stat('Etapas', s.counts.tabs)}
        ${stat('Conversas', s.counts.conversations)}${stat('Perguntas feitas', s.counts.questions)}
      </div></section>
    <section class="block" aria-labelledby="sy2"><h2 class="sec-title" id="sy2">Inteligência artificial (últimos 30 dias)</h2>
      <dl class="kv-list">
        <div class="kv"><dt>Chave configurada</dt><dd>${s.ai.keyConfigured ? 'Sim' : 'Não'}</dd></div>
        <div class="kv"><dt>Modelo</dt><dd>${esc(s.ai.model)}</dd></div>
        <div class="kv"><dt>Chamadas</dt><dd>${s.ai.calls30d} (${s.ai.errors30d} com erro)</dd></div>
        <div class="kv"><dt>Tokens de entrada / saída</dt><dd>${s.ai.inputTokens30d.toLocaleString('pt-BR')} / ${s.ai.outputTokens30d.toLocaleString('pt-BR')}</dd></div>
        <div class="kv"><dt>Custo estimado</dt><dd>${esc(fmtUsd(s.ai.estCostMicroUsd30d))}</dd></div>
      </dl>
      <p class="cost-note">Estimativa calculada pelos tokens usados, com preço de referência de US$ 3 por milhão de tokens de entrada e US$ 15 por milhão de saída. O valor real é o da fatura da Anthropic.</p></section>
    <section class="block" aria-labelledby="sy3"><h2 class="sec-title" id="sy3">Instalação</h2>
      <dl class="kv-list">
        <div class="kv"><dt>Versão</dt><dd>${esc(s.version)}</dd></div>
        <div class="kv"><dt>Banco de dados</dt><dd>${esc(s.database)}</dd></div>
        <div class="kv"><dt>Java</dt><dd>${esc(s.javaVersion)}</dd></div>
        <div class="kv"><dt>No ar há</dt><dd>${esc(fmtDuration(s.uptimeSeconds))}</dd></div>
        <div class="kv"><dt>Arquivos de clientes</dt><dd>${esc(fmtBytes(used))}</dd></div>
        <div class="kv"><dt>Espaço livre em disco</dt><dd>${esc(fmtBytes(s.disk.freeBytes))} de ${esc(fmtBytes(s.disk.totalBytes))}</dd></div>
        <div class="kv"><dt>Envio de e-mail</dt><dd>${s.emailConfigured ? 'Configurado' : 'Não configurado'}
          <button class="btn small" type="button" data-action="sys-test-email" style="margin-left:10px;">Testar e-mail</button></dd></div>
      </dl></section>
    <section class="block" aria-labelledby="sy4"><h2 class="sec-title" id="sy4">Erros recentes</h2>
      ${s.recentErrors.length ? `<div class="card admin-table-wrap"><div class="table-scroll"><table class="admin-table"><caption class="sr-only">Erros recentes</caption>
        <thead><tr><th scope="col">Quando</th><th scope="col">Onde</th><th scope="col">Mensagem</th></tr></thead>
        <tbody>${s.recentErrors.map((e) => `<tr><td data-label="Quando">${esc(fmtDateTime(e.at))}</td><td data-label="Onde">${esc(e.path || '—')}</td><td data-label="Mensagem" class="detail">${esc(e.message)}</td></tr>`).join('')}</tbody></table></div></div>`
        : '<p class="empty-note" style="margin:0;">Nenhum erro registrado. Tudo certo.</p>'}</section>
    <section class="block" aria-labelledby="sy5"><h2 class="sec-title" id="sy5">Últimas chamadas à IA</h2>
      ${s.recentCalls.length ? `<div class="card admin-table-wrap"><div class="table-scroll"><table class="admin-table"><caption class="sr-only">Últimas chamadas à IA</caption>
        <thead><tr><th scope="col">Quando</th><th scope="col">Tipo</th><th scope="col">Resultado</th><th scope="col">Tempo</th><th scope="col">Tokens</th><th scope="col">Custo estimado</th><th scope="col">Observação</th></tr></thead>
        <tbody>${s.recentCalls.map((c) => `<tr><td data-label="Quando">${esc(fmtDateTime(c.at))}</td><td data-label="Tipo">${esc(c.kind)}</td>
          <td data-label="Resultado"><span class="pill ${c.status === 'OK' ? 'teal' : c.status === 'ERROR' ? 'red' : 'amber'}">${esc(c.status)}</span></td>
          <td data-label="Tempo">${c.durationMs == null ? '—' : esc(c.durationMs + ' ms')}</td>
          <td data-label="Tokens">${c.inputTokens == null ? '—' : esc(c.inputTokens + ' / ' + c.outputTokens)}</td>
          <td data-label="Custo estimado">${c.estCostMicroUsd == null ? '—' : esc(fmtUsd(c.estCostMicroUsd))}</td>
          <td data-label="Observação" class="detail">${esc(c.error || '')}</td></tr>`).join('')}</tbody></table></div></div>`
        : '<p class="empty-note" style="margin:0;">Nenhuma chamada à IA ainda.</p>'}</section>`;
}

onClick('sys-test-email', async (el) => {
  await withBusy(el, 'Testando…', async () => {
    try {
      const r = await api.post('/api/admin/system/test-email', {});
      toast(r.message, r.ok ? 'ok' : 'error');
    } catch (error) { toast(error.message, 'error'); }
  });
});
