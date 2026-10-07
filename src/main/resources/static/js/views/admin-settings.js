/* Configurações da consultoria: integração de IA, limites, nome da consultoria e administradores. */
import { api } from '../api.js';
import { esc, fmtDateTime, fmtUsd, onChange, onClick, onSubmit, openModal, confirmDialog, toast, withBusy, toggleTheme, currentTheme } from '../ui.js';
import { setAiBadge, refreshShell, state, applyLogo } from '../main.js';
import { showInvite } from './admin-clients.js';

const MODELS = ['claude-sonnet-4-6', 'claude-opus-4-1', 'claude-haiku-4-5-20251001'];
const SOURCES = {
  database: 'Chave guardada no sistema (cifrada).',
  environment: 'Chave vinda da configuração do servidor. Cadastre uma aqui para substituí-la.',
  none: 'Nenhuma chave cadastrada. O sistema funciona em modo básico, só com busca no texto.',
};

let S = null;

export async function render(root) {
  S = { root };
  await load();
  return () => { S = null; };
}

async function load() {
  const mine = S;
  const [settings, admins, system] = await Promise.all([api.get('/api/admin/settings'), api.get('/api/admin/admins'),
    api.get('/api/admin/system')]);
  if (S !== mine) return;
  S.settings = settings;
  S.admins = admins;
  S.calls = system.recentCalls.slice(0, 20);
  paint();
  setAiBadge(settings.ai.keySource);
}

function adminStatus(a) {
  if (a.suspended) return '<span class="pill amber">Acesso suspenso</span>';
  return a.passwordSet ? '<span class="pill teal">Ativo</span>' : '<span class="pill gray">Convite pendente</span>';
}

function paint() {
  const { ai, limits, organization } = S.settings;
  S.root.innerHTML = `
    <div class="section-head"><h1>Configurações</h1><p>Integração com a IA, limites de uso e quem administra o sistema.</p></div>
    <div class="settings-grid">
      <div class="card settings-card">
        <h2>Inteligência artificial</h2>
        <p class="d">O processamento dos PDFs e as respostas do chat usam a API da Anthropic. A chave fica cifrada no servidor e nunca volta para esta tela.</p>
        <dl class="kv-list" style="margin-bottom:14px;">
          <div class="kv"><dt>Chave em uso</dt><dd>${ai.keyMasked ? '<span class="mono">' + esc(ai.keyMasked) + '</span>' : 'Nenhuma'}</dd></div>
          <div class="kv"><dt>Origem</dt><dd>${esc(SOURCES[ai.keySource] || '')}</dd></div>
        </dl>
        <form data-form="save-ai" novalidate>
          <div id="ai-error" class="form-error hidden" role="alert"></div>
          <div class="field"><label for="ai-key">Nova chave da API</label>
            <input id="ai-key" name="apiKey" type="password" autocomplete="off" placeholder="sk-ant-...">
            <div class="help">Deixe em branco para manter a chave atual.</div></div>
          <div class="field"><label for="ai-model">Modelo</label>
            <input id="ai-model" name="model" type="text" list="ai-models" value="${esc(ai.model)}" autocomplete="off">
            <datalist id="ai-models">${MODELS.map((m) => `<option value="${esc(m)}"></option>`).join('')}</datalist>
            <div class="help">Padrão do sistema: ${esc(ai.defaultModel)}.</div></div>
          <div class="field"><label for="ai-tokens">Tamanho máximo da resposta (tokens)</label>
            <input id="ai-tokens" name="maxTokens" type="number" min="256" max="16000" value="${ai.maxTokens}"></div>
          <div class="field"><label for="ai-temp">Temperatura (0 a 1)</label>
            <input id="ai-temp" name="temperature" type="number" min="0" max="1" step="0.1" value="${ai.temperature}">
            <div class="help">Valores baixos deixam as respostas mais previsíveis.</div></div>
          <div class="row-flex">
            <button class="btn blue" type="submit">Salvar</button>
            <button class="btn" type="button" data-action="ai-test">Testar conexão</button>
            ${ai.keySource === 'database' ? '<button class="btn danger" type="button" data-action="ai-remove-key">Remover chave</button>' : ''}
          </div>
          <div class="status-line" id="ai-test-result" role="status"></div>
        </form>
        <h3 class="sec-title" style="margin-top:22px;">Últimas chamadas</h3>
        ${S.calls.length ? `<div class="table-scroll"><table class="admin-table calls-table"><caption class="sr-only">Últimas chamadas à IA</caption>
          <thead><tr><th scope="col">Quando</th><th scope="col">Tipo</th><th scope="col">Resultado</th><th scope="col">Tempo</th><th scope="col">Custo estimado</th></tr></thead>
          <tbody>${S.calls.map((c) => `<tr><td data-label="Quando">${esc(fmtDateTime(c.at))}</td><td data-label="Tipo">${esc({ CHAT: 'Chat', PROCESS: 'Processamento', TEST: 'Teste' }[c.kind] || c.kind)}</td>
            <td data-label="Resultado"><span class="pill ${c.status === 'OK' ? 'teal' : c.status === 'ERROR' ? 'red' : 'amber'}">${esc(c.status === 'OK' ? 'OK' : c.status === 'ERROR' ? 'Erro' : c.status)}</span></td>
            <td data-label="Tempo">${c.durationMs == null ? '—' : esc(c.durationMs + ' ms')}</td>
            <td data-label="Custo estimado">${c.estCostMicroUsd == null ? '—' : esc(fmtUsd(c.estCostMicroUsd))}</td></tr>`).join('')}</tbody></table></div>`
          : '<p class="empty-note" style="margin:0;">Nenhuma chamada à IA ainda.</p>'}
      </div>

      <div class="card settings-card">
        <h2>Limites de uso</h2>
        <p class="d">Evitam gasto inesperado com a IA. Passar do limite devolve um aviso claro ao usuário.</p>
        <form data-form="save-limits" novalidate>
          <div id="limits-error" class="form-error hidden" role="alert"></div>
          <div class="field"><label for="lim-q">Perguntas por hora, por cliente</label><input id="lim-q" name="questionsPerHour" type="number" min="1" max="1000" value="${limits.questionsPerHour}"></div>
          <div class="field"><label for="lim-p">Processamentos de PDF por dia, por cliente</label><input id="lim-p" name="processingsPerDay" type="number" min="1" max="200" value="${limits.processingsPerDay}"></div>
          <div class="field"><label for="lim-u">Tamanho máximo do PDF (MB)</label><input id="lim-u" name="maxUploadMb" type="number" min="1" max="25" value="${limits.maxUploadMb}"></div>
          <button class="btn blue" type="submit">Salvar limites</button>
        </form>
      </div>

      <div class="card settings-card">
        <h2>Consultoria</h2>
        <p class="d">O nome aparece no topo do painel e na tela de entrada.</p>
        <form data-form="save-org" novalidate>
          <div id="org-error" class="form-error hidden" role="alert"></div>
          <div class="field"><label for="org-name">Nome da consultoria</label><input id="org-name" name="name" type="text" value="${esc(organization.name)}"></div>
          <button class="btn blue" type="submit">Salvar nome</button>
        </form>
        <h3 class="sec-title" style="margin-top:22px;">Logo</h3>
        <div class="logo-preview"><img src="${state.hasLogo ? '/api/public/logo?v=' + Date.now() : 'img/logo-empresa-jr.png'}" alt="Logo atual">
          <span class="muted" style="font-size:13px;">${state.hasLogo ? 'Logo próprio da consultoria.' : 'Usando o logo padrão da Empresa JR.'} PNG, JPEG ou WebP, até 1 MB.</span></div>
        <input type="file" id="logo-file" class="file-input-hidden" accept="image/png,image/jpeg,image/webp" tabindex="-1" aria-label="Escolher o logo" data-change="logo-file">
        <div class="row-flex"><button class="btn" type="button" data-action="logo-pick">Enviar logo</button>
          ${state.hasLogo ? '<button class="btn danger" type="button" data-action="logo-remove">Voltar ao logo padrão</button>' : ''}</div>
      </div>

      <div class="card settings-card">
        <h2>Administradores</h2>
        <p class="d">Quem pode gerenciar clientes, conteúdo e configurações. Novos administradores recebem um link para criar a senha.</p>
        <div class="stack" id="admins-list">${S.admins.map((a) => `
          <div class="toggle-row" style="padding-top:0;"><div><div class="t">${esc(a.name)} ${adminStatus(a)}</div><div class="d2">${esc(a.email)} · último acesso ${esc(fmtDateTime(a.lastLoginAt))}</div></div>
            <div class="row-flex"><button class="btn small" type="button" data-action="admin-invite" data-id="${a.id}" aria-label="Gerar link de acesso de ${esc(a.name)}">Gerar link</button>
            <button class="btn small${a.suspended ? '' : ' danger'}" type="button" data-action="admin-toggle" data-id="${a.id}" data-suspended="${a.suspended}" aria-label="${a.suspended ? 'Reativar' : 'Suspender'} ${esc(a.name)}">${a.suspended ? 'Reativar' : 'Suspender'}</button></div></div>`).join('')}
        </div>
        <button class="btn blue" type="button" data-action="admin-new" style="margin-top:14px;">+ Novo administrador</button>
      </div>

      <div class="card settings-card">
        <h2>Aparência</h2>
        <p class="d">Escolha como a interface é exibida neste navegador.</p>
        <div class="toggle-row">
          <div><div class="t" id="dark-label2">Modo escuro</div><div class="d2">Alterna as cores entre claro e escuro</div></div>
          <button class="switch light${currentTheme() === 'dark' ? ' on' : ''}" type="button" role="switch" aria-checked="${currentTheme() === 'dark'}" aria-labelledby="dark-label2" data-action="settings-theme"></button>
        </div>
      </div>
    </div>`;
}

function show(id, message) {
  const box = document.getElementById(id);
  if (!box) return;
  box.textContent = message;
  box.classList.toggle('hidden', !message);
}

onSubmit('save-ai', async (form) => {
  show('ai-error', '');
  const body = { apiKey: form.apiKey.value.trim() || null, model: form.model.value.trim() || null,
                 maxTokens: form.maxTokens.value ? Number(form.maxTokens.value) : null,
                 temperature: form.temperature.value !== '' ? Number(form.temperature.value) : null };
  await withBusy(form.querySelector('button[type=submit]'), 'Salvando…', async () => {
    try { await api.put('/api/admin/settings/ai', body); await load(); toast('Configuração da IA salva.', 'ok'); }
    catch (error) { show('ai-error', error.message); }
  });
});

onSubmit('save-limits', async (form) => {
  show('limits-error', '');
  await withBusy(form.querySelector('button[type=submit]'), 'Salvando…', async () => {
    try {
      await api.put('/api/admin/settings/limits', { questionsPerHour: Number(form.questionsPerHour.value),
        processingsPerDay: Number(form.processingsPerDay.value), maxUploadMb: Number(form.maxUploadMb.value) });
      await load(); toast('Limites salvos.', 'ok');
    } catch (error) { show('limits-error', error.message); }
  });
});

onSubmit('save-org', async (form) => {
  show('org-error', '');
  await withBusy(form.querySelector('button[type=submit]'), 'Salvando…', async () => {
    try {
      const org = await api.put('/api/admin/settings/organization', { name: form.name.value });
      state.orgName = org.name;
      refreshShell();
      await load(); toast('Nome da consultoria salvo.', 'ok');
    } catch (error) { show('org-error', error.message); }
  });
});

onClick('ai-test', async (el) => {
  const out = document.getElementById('ai-test-result');
  out.textContent = '';
  await withBusy(el, 'Testando…', async () => {
    try {
      const r = await api.post('/api/admin/settings/ai/test', {});
      out.textContent = (r.ok ? '✓ ' : '✕ ') + r.message + (r.ok ? ` (${r.durationMs} ms, modelo ${r.model})` : '');
      out.style.color = r.ok ? 'var(--teal)' : 'var(--red)';
    } catch (error) { out.textContent = '✕ ' + error.message; out.style.color = 'var(--red)'; }
  });
});

onClick('ai-remove-key', async () => {
  const ok = await confirmDialog({ title: 'Remover a chave da IA', message: 'Sem chave, o chat passa a mostrar só trechos do plano e o processamento de PDF não reescreve o conteúdo. Dá para cadastrar outra a qualquer momento.', confirmLabel: 'Remover chave', danger: true });
  if (!ok) return;
  try { await api.del('/api/admin/settings/ai/key'); await load(); toast('Chave removida.', 'ok'); } catch (error) { toast(error.message, 'error'); }
});

onClick('admin-new', async () => {
  const result = await openModal({
    title: 'Novo administrador', description: 'Ele recebe um link para criar a própria senha.',
    body: `<div class="field"><label for="na-name">Nome</label><input id="na-name" type="text" autocomplete="off"></div>
      <div class="field"><label for="na-email">E-mail de acesso</label><input id="na-email" type="email" autocomplete="off"></div>`,
    actions: [{ id: 'cancel', label: 'Cancelar' }, { id: 'save', label: 'Convidar', kind: 'blue', primary: true, handler: async (m) => {
      const body = { name: m.$('#na-name').value.trim(), email: m.$('#na-email').value.trim() };
      if (!body.name || !body.email) { m.error('Informe o nome e o e-mail.'); return; }
      m.busy(true);
      try { m.close(await api.post('/api/admin/admins', body)); } catch (e) { m.busy(false); m.error(e.message); }
    } }],
  });
  if (result && result.inviteToken) {
    await load();
    await showInvite(result, { title: 'Envie o acesso ao administrador', intro: `Copie o link e envie para ${result.client.email}.` });
  }
});

onClick('admin-invite', async (el) => {
  try {
    const invite = await api.post(`/api/admin/admins/${el.dataset.id}/invite`, {});
    await load();
    await showInvite(invite, { title: 'Novo link de acesso', intro: `Link para ${invite.client.email}. O link anterior deixou de funcionar.` });
  } catch (error) { toast(error.message, 'error'); }
});

onClick('admin-toggle', async (el) => {
  const suspend = el.dataset.suspended !== 'true';
  try { await api.post(`/api/admin/admins/${el.dataset.id}/${suspend ? 'suspend' : 'activate'}`, {}); await load(); toast(suspend ? 'Acesso suspenso.' : 'Acesso reativado.', 'ok'); }
  catch (error) { toast(error.message, 'error'); }
});

onClick('logo-pick', () => { const input = document.getElementById('logo-file'); if (input) { input.value = ''; input.click(); } });
onChange('logo-file', async (el) => {
  const file = el.files && el.files[0];
  if (!file) return;
  try { await api.upload('/api/admin/settings/logo', file); applyLogo(true); await load(); toast('Logo atualizado.', 'ok'); }
  catch (error) { toast(error.message, 'error'); }
});
onClick('logo-remove', async () => {
  try { await api.del('/api/admin/settings/logo'); applyLogo(false); await load(); toast('Voltamos ao logo padrão.', 'ok'); }
  catch (error) { toast(error.message, 'error'); }
});

onClick('settings-theme', (el) => {
  toggleTheme();
  const dark = currentTheme() === 'dark';
  el.classList.toggle('on', dark);
  el.setAttribute('aria-checked', String(dark));
});
