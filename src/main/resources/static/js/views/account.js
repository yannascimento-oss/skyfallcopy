/* Minha conta: dados de acesso, troca de senha e aparência. */
import { api } from '../api.js';
import { esc, onSubmit, onClick, toast, toggleTheme, currentTheme, withBusy } from '../ui.js';

function localProblem(next, confirm) {
  if (next.length < 10) return 'A nova senha precisa ter pelo menos 10 caracteres.';
  if (!/[A-Za-z]/.test(next) || !/\d/.test(next)) return 'A nova senha precisa misturar letras e números.';
  if (next !== confirm) return 'As duas senhas novas não são iguais.';
  return '';
}

function setError(message) {
  const box = document.getElementById('pw-error');
  if (!box) return;
  box.textContent = message;
  box.classList.toggle('hidden', !message);
}

export async function render(root, { me, state }) {
  const isAdmin = me.role === 'ADMIN';
  root.innerHTML = `
    <div class="section-head"><h1>Minha conta</h1><p>Seus dados de acesso e preferências.</p></div>
    <div class="settings-grid">
      <div class="card settings-card">
        <h2>Perfil</h2>
        <p class="d">Para alterar nome ou e-mail, fale com a consultoria.</p>
        <dl class="kv-list">
          <div class="kv"><dt>Nome</dt><dd>${esc(me.name)}</dd></div>
          <div class="kv"><dt>E-mail de acesso</dt><dd>${esc(me.email)}</dd></div>
          <div class="kv"><dt>${isAdmin ? 'Perfil' : 'Empresa'}</dt><dd>${esc(isAdmin ? 'Administrador' : me.company)}</dd></div>
        </dl>
      </div>
      <div class="card settings-card">
        <h2>Trocar senha</h2>
        <p class="d">Use pelo menos 10 caracteres, misturando letras e números.</p>
        <form data-form="change-password" novalidate>
          <div id="pw-error" class="form-error hidden" role="alert"></div>
          <div class="field"><label for="pw-current">Senha atual</label><input id="pw-current" name="current" type="password" autocomplete="current-password" required></div>
          <div class="field"><label for="pw-new">Nova senha</label><input id="pw-new" name="next" type="password" autocomplete="new-password" required></div>
          <div class="field"><label for="pw-confirm">Repita a nova senha</label><input id="pw-confirm" name="confirm" type="password" autocomplete="new-password" required></div>
          <button class="btn blue" type="submit">Salvar nova senha</button>
        </form>
      </div>
      <div class="card settings-card">
        <h2>Aparência</h2>
        <p class="d">Escolha como a interface é exibida.</p>
        <div class="toggle-row">
          <div><div class="t" id="dark-label">Modo escuro</div><div class="d2">Alterna as cores entre claro e escuro</div></div>
          <button class="switch light${currentTheme() === 'dark' ? ' on' : ''}" id="theme-switch" type="button" role="switch"
            aria-checked="${currentTheme() === 'dark'}" aria-labelledby="dark-label" data-action="account-theme"></button>
        </div>
      </div>
    </div>`;
}

onSubmit('change-password', async (form) => {
  const current = form.current.value, next = form.next.value;
  if (!current) { setError('Informe a senha atual.'); return; }
  const problem = localProblem(next, form.confirm.value);
  if (problem) { setError(problem); return; }
  setError('');
  await withBusy(form.querySelector('button[type=submit]'), 'Salvando…', async () => {
    try {
      await api.post('/api/me/password', { currentPassword: current, newPassword: next });
      form.reset();
      toast('Senha alterada com sucesso.', 'ok');
    } catch (error) { setError(error.message); }
  });
});

onClick('account-theme', (el) => {
  toggleTheme();
  const dark = currentTheme() === 'dark';
  el.classList.toggle('on', dark);
  el.setAttribute('aria-checked', String(dark));
});
