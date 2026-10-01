/* Clientes & Planos: cadastro, acesso, edição e exclusão. */
import { api } from '../api.js';
import { esc, initials, fmtDateTime, onClick, openModal, confirmDialog, copyText, toast } from '../ui.js';

let S = null;

function statusPill(c) {
  if (c.suspended) return '<span class="pill amber">Acesso suspenso</span>';
  if (!c.passwordSet) return '<span class="pill gray">Convite pendente</span>';
  return '<span class="pill teal">Ativo</span>';
}

export async function render(root) {
  S = { root, clients: [] };
  root.innerHTML = `
    <div class="section-head row-flex" style="align-items:flex-end;justify-content:space-between;">
      <div><h1>Clientes &amp; Planos</h1><p>Crie acessos, gerencie o acesso de cada cliente e abra o conteúdo do plano dele.</p></div>
      <button class="btn blue" type="button" data-action="client-new">+ Novo cliente</button>
    </div>
    <div class="card admin-table-wrap"><div class="table-scroll" id="clients-body"></div></div>`;
  await load();
  return () => { S = null; };
}

async function load() {
  const mine = S;
  const clients = await api.get('/api/admin/clients');
  if (S !== mine) return;
  S.clients = clients;
  paint();
}

function paint() {
  const body = document.getElementById('clients-body');
  if (!body) return;
  if (!S.clients.length) {
    body.innerHTML = '<p class="empty-note" style="padding:26px;margin:0;">Nenhum cliente cadastrado ainda. Clique em "Novo cliente" para criar o primeiro acesso.</p>';
    return;
  }
  body.innerHTML = `<table class="admin-table"><caption class="sr-only">Clientes cadastrados</caption>
    <thead><tr><th scope="col">Cliente</th><th scope="col">Empresa</th><th scope="col">E-mail de acesso</th><th scope="col">Plano</th><th scope="col">Status</th><th scope="col">Último acesso</th><th scope="col">Ações</th></tr></thead>
    <tbody>${S.clients.map((c) => `<tr>
      <td><div class="client-cell"><div class="mini-avatar" aria-hidden="true">${esc(initials(c.name))}</div><span class="nm"><b>${esc(c.name)}</b></span></div></td>
      <td data-label="Empresa">${esc(c.company)}${c.segment ? `<br><span class="muted">${esc(c.segment)}</span>` : ''}</td>
      <td data-label="E-mail de acesso">${esc(c.email)}</td>
      <td data-label="Plano">${c.planVersion ? 'v' + c.planVersion : 'Sem conteúdo'}</td>
      <td data-label="Status">${statusPill(c)}</td>
      <td data-label="Último acesso">${esc(fmtDateTime(c.lastLoginAt))}</td>
      <td><div class="row-actions">
        <a class="btn blue" href="#/conteudo/${c.id}" aria-label="Gerenciar conteúdo de ${esc(c.company)}">Conteúdo</a>
        <a class="btn" href="#/indicadores/${c.id}" aria-label="Ver indicadores de ${esc(c.company)}">Indicadores</a>
        <button class="btn" type="button" data-action="client-access" data-id="${c.id}" aria-label="Gerenciar acesso de ${esc(c.company)}">Acesso</button>
      </div></td></tr>`).join('')}</tbody></table>`;
}

/** Mostra o link de convite com botão de copiar. O token só existe nesta resposta. */
export async function showInvite(invite, { title, intro }) {
  const link = location.origin + invite.invitePath;
  await openModal({
    title, description: intro,
    body: `<div class="field" style="margin-top:12px;"><label for="invite-link">Link de acesso</label>
      <div class="invite-box"><input id="invite-link" type="text" readonly value="${esc(link)}"><button class="btn blue" type="button" data-action="copy-link" data-target="invite-link">Copiar link</button></div>
      <div class="help">O link vale por 48 horas e funciona uma única vez. O sistema não envia e-mail: copie e envie você mesmo.</div></div>`,
    actions: [{ id: 'ok', label: 'Fechar', primary: true }],
    wide: true,
  }).then(() => null);
}

onClick('client-new', async () => {
  const result = await openModal({
    title: 'Novo cliente',
    description: 'Crie o acesso do cliente. O plano nasce com as etapas padrão, prontas para receber o material.',
    body: `<div class="field"><label for="nc-name">Nome do responsável</label><input type="text" id="nc-name" autocomplete="off" placeholder="Ex: Rafael Matos"></div>
      <div class="field"><label for="nc-email">E-mail de acesso</label><input type="email" id="nc-email" autocomplete="off" placeholder="rafael@empresa.com"></div>
      <div class="field"><label for="nc-company">Nome da empresa</label><input type="text" id="nc-company" autocomplete="off" placeholder="Ex: Cafeteria Grão & Cia"></div>
      <div class="field"><label for="nc-segment">Segmento (opcional)</label><input type="text" id="nc-segment" autocomplete="off" placeholder="Ex: Alimentação"></div>`,
    actions: [
      { id: 'cancel', label: 'Cancelar' },
      { id: 'save', label: 'Cadastrar cliente', kind: 'blue', primary: true, handler: async (m) => {
        const body = { name: m.$('#nc-name').value.trim(), email: m.$('#nc-email').value.trim(),
                       company: m.$('#nc-company').value.trim(), segment: m.$('#nc-segment').value.trim() || null };
        if (!body.name || !body.email || !body.company) { m.error('Preencha o nome do responsável, o e-mail e a empresa.'); return; }
        m.busy(true);
        try { m.close(await api.post('/api/admin/clients', body)); } catch (e) { m.busy(false); m.error(e.message); }
      } },
    ],
  });
  if (result && result.inviteToken) {
    await load();
    toast('Cliente cadastrado.', 'ok');
    await showInvite(result, { title: 'Envie o acesso ao cliente', intro: `Cadastro de ${result.client.company} concluído. Copie o link e envie para ${result.client.email}.` });
  }
});

onClick('client-access', async (el) => {
  if (!S) return;
  const c = S.clients.find((x) => x.id === Number(el.dataset.id));
  if (!c) return;
  const choice = await openModal({
    title: c.company, wide: true,
    description: `${c.name} · ${c.email}`,
    body: `<dl class="kv-list" style="margin-bottom:14px;"><div class="kv"><dt>Status</dt><dd>${statusPill(c)}</dd></div>
      <div class="kv"><dt>Último acesso</dt><dd>${esc(fmtDateTime(c.lastLoginAt))}</dd></div></dl>
      <div class="stack">
        <button class="btn" type="button" data-modal-action="invite">${c.passwordSet ? 'Gerar link para redefinir a senha' : 'Gerar novo link de convite'}</button>
        <button class="btn" type="button" data-modal-action="edit">Editar dados do cliente</button>
        <button class="btn" type="button" data-modal-action="${c.suspended ? 'activate' : 'suspend'}">${c.suspended ? 'Reativar acesso' : 'Suspender acesso'}</button>
        <button class="btn danger" type="button" data-modal-action="delete">Excluir cliente e todo o plano</button>
      </div>`,
    actions: [{ id: 'close', label: 'Fechar', primary: true }],
  });
  if (choice === 'invite') {
    try {
      const invite = await api.post(`/api/admin/clients/${c.id}/invite`, {});
      await load();
      await showInvite(invite, { title: 'Novo link de acesso', intro: `Link para ${c.email}. O link anterior deixou de funcionar.` });
    } catch (e) { toast(e.message, 'error'); }
  } else if (choice === 'suspend' || choice === 'activate') {
    const suspend = choice === 'suspend';
    const ok = await confirmDialog({
      title: suspend ? 'Suspender acesso' : 'Reativar acesso',
      message: suspend ? `${c.name} deixará de conseguir entrar, e a sessão aberta é encerrada na próxima ação.` : `${c.name} voltará a conseguir entrar com a mesma senha.`,
      confirmLabel: suspend ? 'Suspender' : 'Reativar', danger: suspend,
    });
    if (!ok) return;
    try { await api.post(`/api/admin/clients/${c.id}/${choice}`, {}); await load(); toast(suspend ? 'Acesso suspenso.' : 'Acesso reativado.', 'ok'); }
    catch (e) { toast(e.message, 'error'); }
  } else if (choice === 'edit') {
    await editClient(c);
  } else if (choice === 'delete') {
    await deleteClient(c);
  }
});

async function editClient(c) {
  const saved = await openModal({
    title: 'Editar cliente', description: 'O e-mail de acesso não muda aqui.',
    body: `<div class="field"><label for="ec-name">Nome do responsável</label><input type="text" id="ec-name" value="${esc(c.name)}"></div>
      <div class="field"><label for="ec-company">Nome da empresa</label><input type="text" id="ec-company" value="${esc(c.company)}"></div>
      <div class="field"><label for="ec-segment">Segmento</label><input type="text" id="ec-segment" value="${esc(c.segment || '')}"></div>`,
    actions: [
      { id: 'cancel', label: 'Cancelar' },
      { id: 'save', label: 'Salvar', kind: 'blue', primary: true, handler: async (m) => {
        const body = { name: m.$('#ec-name').value.trim(), company: m.$('#ec-company').value.trim(), segment: m.$('#ec-segment').value.trim() || null };
        if (!body.name || !body.company) { m.error('Informe o nome do responsável e da empresa.'); return; }
        m.busy(true);
        try { m.close(await api.put(`/api/admin/clients/${c.id}`, body)); } catch (e) { m.busy(false); m.error(e.message); }
      } },
    ],
  });
  if (saved) { await load(); toast('Dados atualizados.', 'ok'); }
}

async function deleteClient(c) {
  const done = await openModal({
    title: 'Excluir cliente', danger: true,
    description: `Isto apaga ${c.company}: acesso, plano, anexos e conversas. Não dá para desfazer. Para confirmar, digite o e-mail do cliente.`,
    body: `<div class="field"><label for="dc-email">E-mail do cliente</label><input type="text" id="dc-email" autocomplete="off" placeholder="${esc(c.email)}"></div>`,
    actions: [
      { id: 'cancel', label: 'Cancelar' },
      { id: 'delete', label: 'Excluir definitivamente', kind: 'danger', primary: true, handler: async (m) => {
        const typed = m.$('#dc-email').value.trim();
        if (!typed) { m.error('Digite o e-mail do cliente para confirmar.'); return; }
        m.busy(true);
        try { await api.del(`/api/admin/clients/${c.id}?confirmEmail=${encodeURIComponent(typed)}`); m.close(true); }
        catch (e) { m.busy(false); m.error(e.message); }
      } },
    ],
  });
  if (done) { await load(); toast('Cliente excluído.', 'ok'); }
}

onClick('copy-link', async (el) => {
  const input = document.getElementById(el.dataset.target);
  if (!input) return;
  input.select();
  const ok = await copyText(input.value);
  toast(ok ? 'Link copiado.' : 'Não foi possível copiar sozinho. Selecione o texto e copie com Ctrl+C.', ok ? 'ok' : 'error');
});
