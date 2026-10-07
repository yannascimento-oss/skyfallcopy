/* Entrada: login, instalação inicial (primeiro acesso ao sistema) e definição de senha pelo link de convite. */
import { api, ApiError } from '../api.js';
import { $, esc, onClick, onSubmit, withBusy, toast } from '../ui.js';
import { enterApp, state, applyLogo } from '../main.js';

const POLICY_HINT = 'Mínimo de 10 caracteres, misturando letras e números.';

let mode = 'login';
let token = null;

function card() { return $('#auth-card'); }

function errorBox(id) { return `<div id="${id}" class="form-error hidden" role="alert"></div>`; }

function fail(id, message) {
  const box = document.getElementById(id);
  if (!box) return;
  box.textContent = message;
  box.classList.toggle('hidden', !message);
}

function localPasswordProblem(password, confirm) {
  if (password.length < 10) return 'A senha precisa ter pelo menos 10 caracteres.';
  if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) return 'A senha precisa misturar letras e números.';
  if (password !== confirm) return 'As duas senhas não são iguais.';
  return '';
}

const VIEWS = {
  login: (opts) => `
    <form data-form="login" novalidate>
      <h2>Acessar meu plano</h2>
      <p class="sub">Entre com o e-mail de acesso informado pela consultoria.</p>
      ${opts.notice ? `<div class="form-error" role="status">${esc(opts.notice)}</div>` : ''}
      ${errorBox('auth-error')}
      <div class="field"><label for="login-email">E-mail de acesso</label>
        <input id="login-email" name="email" type="email" autocomplete="username" placeholder="voce@empresa.com" required></div>
      <div class="field">
        <div class="field-row"><label for="login-pass">Senha</label><button class="link-btn" type="button" data-action="auth-forgot">Esqueci minha senha</button></div>
        <input id="login-pass" name="password" type="password" autocomplete="current-password" placeholder="Sua senha" required>
      </div>
      <button class="btn-lg blue btn" type="submit">Entrar no Chat Jr</button>
      <p class="auth-note">Ainda não tem acesso? <button class="link-btn" type="button" data-action="auth-request">Solicitar acesso</button></p>
    </form>`,

  request: () => `
    <form data-form="request-access" novalidate>
      <h2>Solicitar acesso</h2>
      <p class="sub">Deixe seu contato. A consultoria da Empresa JR analisa o pedido e envia o link de acesso ao seu Plano de Negócios.</p>
      ${errorBox('auth-error')}
      <div class="field"><label for="rq-name">Seu nome</label><input id="rq-name" name="name" type="text" autocomplete="name" maxlength="120" required></div>
      <div class="field"><label for="rq-email">E-mail</label><input id="rq-email" name="email" type="email" autocomplete="email" maxlength="200" required></div>
      <div class="field"><label for="rq-company">Empresa</label><input id="rq-company" name="company" type="text" autocomplete="organization" maxlength="160" required></div>
      <div class="field"><label for="rq-phone">Telefone (opcional)</label><input id="rq-phone" name="phone" type="tel" autocomplete="tel" maxlength="40"></div>
      <div class="field"><label for="rq-message">Mensagem (opcional)</label><textarea id="rq-message" name="message" rows="3" maxlength="1000"></textarea></div>
      <div class="hp-field" aria-hidden="true"><label for="rq-website">Deixe em branco</label><input id="rq-website" name="website" type="text" tabindex="-1" autocomplete="off"></div>
      <button class="btn-lg blue btn" type="submit">Enviar pedido</button>
      <div class="auth-switch"><button class="link-btn" type="button" data-action="auth-login">Voltar para o login</button></div>
    </form>`,

  requested: () => `
    <div>
      <h2>Pedido enviado</h2>
      <p class="sub">Recebemos o seu pedido. A consultoria da Empresa JR vai analisar e, se for o caso, enviar o link de acesso para o seu e-mail.</p>
      <div class="auth-switch"><button class="link-btn" type="button" data-action="auth-login">Voltar para o login</button></div>
    </div>`,

  forgot: () => `
    <div>
      <h2>Recuperar senha</h2>
      <p class="sub">O acesso ao Chat Jr é criado pela consultoria. Para redefinir sua senha, fale com a equipe da Empresa JR
        pelo e-mail contato@empresajr.org e informe o seu e-mail de acesso. Ela envia um link para você criar uma nova senha.</p>
      <div class="auth-switch"><button class="link-btn" type="button" data-action="auth-login">Voltar para o login</button></div>
    </div>`,

  setup: () => `
    <form data-form="setup" novalidate>
      <h2>Instalação inicial</h2>
      <p class="sub">Primeiro acesso ao sistema. Cadastre a consultoria e o administrador principal. Isso só é feito uma vez.</p>
      ${errorBox('auth-error')}
      <div class="field"><label for="st-org">Nome da consultoria</label><input id="st-org" name="orgName" type="text" autocomplete="organization" value="Empresa JR" required></div>
      <div class="field"><label for="st-name">Seu nome</label><input id="st-name" name="adminName" type="text" autocomplete="name" required></div>
      <div class="field"><label for="st-email">Seu e-mail (será o login)</label><input id="st-email" name="adminEmail" type="email" autocomplete="username" required></div>
      <div class="field"><label for="st-pass">Senha</label><input id="st-pass" name="password" type="password" autocomplete="new-password" aria-describedby="st-pass-help" required>
        <div class="help" id="st-pass-help">${POLICY_HINT}</div></div>
      <div class="field"><label for="st-pass2">Repita a senha</label><input id="st-pass2" name="confirm" type="password" autocomplete="new-password" required></div>
      <div class="field"><label for="st-logo">Logo da consultoria (opcional)</label><input id="st-logo" name="logo" type="file" accept="image/png,image/jpeg,image/webp">
        <div class="help">PNG, JPEG ou WebP, até 1 MB. Sem logo, o sistema usa o da Empresa JR.</div></div>
      <div class="field"><label for="st-key">Chave da IA (opcional)</label><input id="st-key" name="aiKey" type="password" autocomplete="off" placeholder="sk-ant-...">
        <div class="help">Pode ser cadastrada depois em Configurações. Sem ela, o sistema funciona em modo básico, só com busca no texto.</div></div>
      <button class="btn-lg blue btn" type="submit">Concluir instalação</button>
    </form>`,

  invite: () => `
    <form data-form="invite" novalidate>
      <h2>Crie sua senha</h2>
      <p class="sub">Você recebeu um convite da consultoria. Escolha uma senha para acessar o seu Plano de Negócios.</p>
      ${errorBox('auth-error')}
      <div class="field"><label for="iv-pass">Nova senha</label><input id="iv-pass" name="password" type="password" autocomplete="new-password" aria-describedby="iv-help" required>
        <div class="help" id="iv-help">${POLICY_HINT}</div></div>
      <div class="field"><label for="iv-pass2">Repita a senha</label><input id="iv-pass2" name="confirm" type="password" autocomplete="new-password" required></div>
      <button class="btn-lg blue btn" type="submit">Salvar senha</button>
      <div class="auth-switch"><button class="link-btn" type="button" data-action="auth-login">Já tenho senha</button></div>
    </form>`,

  offline: (opts) => `
    <div>
      <h2>Sem conexão</h2>
      <div class="form-error" role="alert">${esc(opts.message || 'Não foi possível falar com o servidor.')}</div>
      <button class="btn-lg blue btn" type="button" data-action="auth-retry">Tentar de novo</button>
    </div>`,
};

export function showAuth(nextMode, opts = {}) {
  mode = nextMode;
  if (opts.token !== undefined) token = opts.token;
  $('#app').classList.add('hidden');
  $('#view-auth').classList.remove('hidden');
  card().innerHTML = VIEWS[mode](opts);
  const first = card().querySelector('input');
  if (first && opts.focus !== false && document.activeElement === document.body) { /* não rouba o foco ao abrir a página */ }
}

async function doLogin(form) {
  const email = form.email.value.trim();
  const password = form.password.value;
  if (!email || !password) { fail('auth-error', 'Informe o e-mail de acesso e a senha.'); return; }
  fail('auth-error', '');
  const button = form.querySelector('button[type=submit]');
  await withBusy(button, 'Entrando…', async () => {
    try {
      const me = await api.post('/api/auth/login', { email, password }, { allow401: true });
      state.orgName = me.orgName || state.orgName;
      await enterApp(me);
    } catch (error) {
      fail('auth-error', error.message);
    }
  });
}

async function doSetup(form) {
  const problem = localPasswordProblem(form.password.value, form.confirm.value);
  if (!form.orgName.value.trim() || !form.adminName.value.trim() || !form.adminEmail.value.trim()) {
    fail('auth-error', 'Preencha o nome da consultoria, o seu nome e o seu e-mail.'); return;
  }
  if (problem) { fail('auth-error', problem); return; }
  fail('auth-error', '');
  const button = form.querySelector('button[type=submit]');
  await withBusy(button, 'Instalando…', async () => {
    try {
      await api.post('/api/setup', {
        orgName: form.orgName.value.trim(), adminName: form.adminName.value.trim(),
        adminEmail: form.adminEmail.value.trim(), password: form.password.value,
        aiKey: form.aiKey.value.trim() || null,
      });
      const me = await api.post('/api/auth/login', { email: form.adminEmail.value.trim(), password: form.password.value }, { allow401: true });
      state.orgName = me.orgName || form.orgName.value.trim();
      const logo = form.logo.files && form.logo.files[0];
      if (logo) {
        try { await api.upload('/api/admin/settings/logo', logo); applyLogo(true); }
        catch (error) { toast('A instalação foi concluída, mas o logo não foi aceito: ' + error.message + ' Envie de novo em Configurações.', 'error'); }
      }
      await enterApp(me);
    } catch (error) {
      fail('auth-error', error.message);
    }
  });
}

async function doInvite(form) {
  const problem = localPasswordProblem(form.password.value, form.confirm.value);
  if (problem) { fail('auth-error', problem); return; }
  if (!token) { fail('auth-error', 'O link de acesso está incompleto. Peça um novo à consultoria.'); return; }
  fail('auth-error', '');
  const button = form.querySelector('button[type=submit]');
  await withBusy(button, 'Salvando…', async () => {
    try {
      await api.post('/api/auth/accept-invite', { token, password: form.password.value });
      history.replaceState(null, '', location.pathname);
      token = null;
      showAuth('login', { notice: 'Senha criada. Agora é só entrar com o seu e-mail e a nova senha.' });
    } catch (error) {
      fail('auth-error', error.message);
    }
  });
}

onSubmit('login', doLogin);
onSubmit('setup', doSetup);
onSubmit('invite', doInvite);
onSubmit('request-access', async (form) => {
  const body = { name: form.name.value.trim(), email: form.email.value.trim(), company: form.company.value.trim(),
                 phone: form.phone.value.trim() || null, message: form.message.value.trim() || null, website: form.website.value };
  if (!body.name || !body.email || !body.company) { fail('auth-error', 'Preencha seu nome, e-mail e empresa.'); return; }
  fail('auth-error', '');
  await withBusy(form.querySelector('button[type=submit]'), 'Enviando…', async () => {
    try { await api.post('/api/access-requests', body); showAuth('requested'); }
    catch (error) { fail('auth-error', error.message); }
  });
});
onClick('auth-request', () => showAuth('request'));
onClick('auth-forgot', () => showAuth('forgot'));
onClick('auth-login', () => showAuth('login'));
onClick('auth-retry', () => location.reload());
