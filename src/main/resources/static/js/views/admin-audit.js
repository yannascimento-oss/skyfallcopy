/* Histórico: quem fez o quê, em qual cliente e em qual etapa. */
import { api } from '../api.js';
import { esc, fmtDateTime, onChange, onClick } from '../ui.js';

const ACTIONS = {
  SETUP: 'Instalação inicial', LOGIN: 'Entrada no sistema', PASSWORD_SET: 'Senha definida', PASSWORD_CHANGED: 'Senha alterada',
  PASSWORD_RESET_ISSUED: 'Link de redefinição emitido', INVITE_ISSUED: 'Convite emitido',
  CLIENT_CREATED: 'Cliente cadastrado', CLIENT_UPDATED: 'Cliente editado', CLIENT_DELETED: 'Cliente excluído',
  ACCESS_SUSPENDED: 'Acesso suspenso', ACCESS_RESTORED: 'Acesso reativado', ADMIN_CREATED: 'Administrador convidado',
  TAB_CREATED: 'Etapa criada', TAB_UPDATED: 'Etapa editada', TAB_PUBLISHED: 'Etapa publicada', TAB_UNPUBLISHED: 'Etapa despublicada',
  TAB_DELETED: 'Etapa excluída', TAB_RESTORED: 'Etapa restaurada', TAB_PROCESSED: 'Etapa processada', SCOPE_CHANGED: 'Escopo alterado',
  PDF_UPLOADED: 'PDF enviado', SLIDE_LINK_SET: 'Link de slides', ATTACHMENT_REMOVED: 'Anexo removido', PROCESS_STARTED: 'Processamento iniciado',
  AI_SETTINGS_CHANGED: 'IA configurada', AI_KEY_REMOVED: 'Chave da IA removida', AI_TESTED: 'Conexão com a IA testada',
  LIMITS_CHANGED: 'Limites alterados', ORGANIZATION_CHANGED: 'Consultoria renomeada', EXPORT_JSON: 'Exportação JSON', EXPORT_PDF: 'Exportação PDF',
};

let S = null;

export async function render(root) {
  S = { root, page: 0, size: 25, clientId: '', action: '' };
  const clients = await api.get('/api/admin/clients');
  root.innerHTML = `
    <div class="section-head"><h1>Histórico</h1><p>Registro de ações feitas no sistema, do mais recente para o mais antigo.</p></div>
    <div class="row-flex" style="margin-bottom:16px;">
      <div class="field" style="margin:0;min-width:220px;"><label for="au-client">Cliente</label>
        <select id="au-client" data-change="audit-filter"><option value="">Todos</option>
        ${clients.map((c) => `<option value="${c.id}">${esc(c.company || c.name)}</option>`).join('')}</select></div>
      <div class="field" style="margin:0;min-width:220px;"><label for="au-action">Ação</label>
        <select id="au-action" data-change="audit-filter"><option value="">Todas</option>
        ${Object.entries(ACTIONS).map(([k, v]) => `<option value="${k}">${esc(v)}</option>`).join('')}</select></div>
    </div>
    <div class="card admin-table-wrap"><div class="table-scroll" id="audit-body"></div></div>
    <div class="pager" id="audit-pager"></div>`;
  await load();
  return () => { S = null; };
}

async function load() {
  if (!S) return;
  const mine = S;
  const body = document.getElementById('audit-body');
  if (!body) return;
  const query = new URLSearchParams({ page: S.page, size: S.size });
  if (S.clientId) query.set('clientId', S.clientId);
  if (S.action) query.set('action', S.action);
  let data;
  try { data = await api.get('/api/admin/audit?' + query); } catch (error) {
    if (S !== mine) return;
    body.innerHTML = `<div class="form-error" role="alert">${esc(error.message)}</div>`; return;
  }
  if (S !== mine) return;
  body.innerHTML = data.items.length ? `<table class="admin-table audit-table"><caption class="sr-only">Histórico de ações</caption>
    <thead><tr><th scope="col">Quando</th><th scope="col">Quem</th><th scope="col">Ação</th><th scope="col">Cliente</th><th scope="col">Etapa</th><th scope="col">Detalhe</th></tr></thead>
    <tbody>${data.items.map((a) => `<tr>
      <td data-label="Quando">${esc(fmtDateTime(a.createdAt))}</td>
      <td data-label="Quem">${esc(a.actorEmail || 'Sistema')}</td>
      <td data-label="Ação"><span class="pill blue">${esc(ACTIONS[a.action] || a.action)}</span></td>
      <td data-label="Cliente">${esc(a.clientName || '—')}</td>
      <td data-label="Etapa">${esc(a.tabName || '—')}</td>
      <td data-label="Detalhe" class="detail">${esc(a.detail || '')}</td></tr>`).join('')}</tbody></table>`
    : '<p class="empty-note" style="padding:26px;margin:0;">Nenhum registro para este filtro.</p>';
  const pages = Math.max(1, Math.ceil(data.total / data.size));
  document.getElementById('audit-pager').innerHTML = `<span>${data.total} registro${data.total === 1 ? '' : 's'} · página ${data.page + 1} de ${pages}</span>
    <button class="btn small" type="button" data-action="audit-prev"${data.page <= 0 ? ' disabled' : ''}>Anterior</button>
    <button class="btn small" type="button" data-action="audit-next"${data.page + 1 >= pages ? ' disabled' : ''}>Próxima</button>`;
}

onChange('audit-filter', () => {
  if (!S) return;
  S.clientId = document.getElementById('au-client').value;
  S.action = document.getElementById('au-action').value;
  S.page = 0;
  load();
});
onClick('audit-prev', () => { if (!S) return; S.page = Math.max(0, S.page - 1); load(); });
onClick('audit-next', () => { if (!S) return; S.page += 1; load(); });
