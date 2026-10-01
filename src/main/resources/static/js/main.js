/* Ponto de entrada: sessão, menu por perfil e navegação por endereço (#/tela/argumento). */
import { api, setSessionExpiredHandler } from './api.js';
import {
  $, esc, initials, toast, installDelegation, onClick, applyStoredTheme, toggleTheme, confirmDialog,
} from './ui.js';
import { ICONS } from './icons.js';
import { showAuth } from './views/auth.js';

export const state = { me: null, orgName: null, aiSource: null };

const VIEWS = {
  inicio:      { title: 'Início',               module: 'dashboard',       roles: ['CLIENT'] },
  chat:        { title: 'Consultar plano',      module: 'chat',            roles: ['CLIENT'] },
  plano:       { title: 'Plano de negócios',    module: 'plan',            roles: ['CLIENT'] },
  indicadores: { title: 'Indicadores',          module: 'indicators',      roles: ['CLIENT', 'ADMIN'] },
  conta:       { title: 'Minha conta',          module: 'account',         roles: ['CLIENT', 'ADMIN'] },
  clientes:    { title: 'Clientes & Planos',    module: 'admin-clients',   roles: ['ADMIN'] },
  conteudo:    { title: 'Conteúdo dos planos',  module: 'admin-content',   roles: ['ADMIN'] },
  historico:   { title: 'Histórico',            module: 'admin-audit',     roles: ['ADMIN'] },
  config:      { title: 'Configurações',        module: 'admin-settings',  roles: ['ADMIN'] },
  sistema:     { title: 'Sistema',              module: 'admin-system',    roles: ['ADMIN'] },
};

const MENU = {
  CLIENT: [
    { label: 'Principal', items: ['inicio', 'chat', 'plano', 'indicadores'] },
    { label: 'Conta', items: ['conta'] },
  ],
  ADMIN: [
    { label: 'Administração', items: ['clientes', 'conteudo', 'indicadores'] },
    { label: 'Gestão', items: ['historico', 'config', 'sistema'] },
    { label: 'Conta', items: ['conta'] },
  ],
};

let cleanup = null;
let routeSeq = 0;

export function parseHash() {
  const [id, ...rest] = (location.hash.replace(/^#\/?/, '') || '').split('/');
  return { id, arg: rest.join('/') || null };
}

export function go(id, arg) {
  const target = '#/' + id + (arg ? '/' + arg : '');
  if (location.hash === target) route(); else location.hash = target;
}

function defaultView() { return state.me.role === 'ADMIN' ? 'clientes' : 'inicio'; }

function buildMenu() {
  const nav = $('#side-nav');
  nav.innerHTML = MENU[state.me.role].map((group) =>
    `<div class="nav-group-label">${esc(group.label)}</div>` + group.items.map((id) =>
      `<a class="nav-item" href="#/${id}" data-view="${id}">${ICONS[id] || ''}${esc(VIEWS[id].title)}</a>`).join('')).join('');
}

function updateShell() {
  const me = state.me;
  const isAdmin = me.role === 'ADMIN';
  $('#side-company-label').textContent = isAdmin ? 'Consultoria' : 'Empresa ativa';
  $('#active-company').textContent = isAdmin ? (state.orgName || 'Empresa JR') : (me.company || '');
  $('#side-avatar').textContent = initials(me.name);
  $('#top-avatar').textContent = initials(me.name);
  $('#side-user-name').textContent = me.name;
  $('#side-user-role').textContent = isAdmin ? 'Administrador' : (me.company || 'Cliente');
  $('#ai-badge').classList.toggle('hidden', !isAdmin);
}

export function setAiBadge(source) {
  state.aiSource = source;
  const badge = $('#ai-badge');
  const on = source && source !== 'none';
  badge.classList.toggle('off', !on);
  badge.classList.toggle('on', !!on);
  badge.textContent = on ? 'IA conectada' : 'IA sem chave';
}

async function refreshAiBadge() {
  if (state.me.role !== 'ADMIN') return;
  try { setAiBadge((await api.get('/api/admin/settings')).ai.keySource); } catch (e) { /* o selo é só informativo */ }
}

async function route() {
  if (!state.me) return;
  const seq = ++routeSeq;
  let { id, arg } = parseHash();
  if (!VIEWS[id] || !VIEWS[id].roles.includes(state.me.role)) {
    id = defaultView();
    history.replaceState(null, '', '#/' + id);
    arg = null;
  }
  if (cleanup) { try { cleanup(); } catch (e) { /* tela já saiu */ } cleanup = null; }
  const view = VIEWS[id];
  document.querySelectorAll('.nav-item').forEach((a) => {
    const active = a.dataset.view === id;
    a.classList.toggle('active', active);
    if (active) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
  });
  $('#page-title').textContent = view.title;
  $('#page-crumb').textContent = state.me.role === 'ADMIN' ? (state.orgName || 'Empresa JR') : (state.me.company || '');
  document.title = view.title + ' · Chat Jr';
  toggleSidebar(false);

  // Cada navegação ganha o próprio contêiner: respostas atrasadas de uma tela que já saiu caem num elemento
  // descartado e nunca sobrescrevem a tela atual.
  const content = $('#content');
  const host = document.createElement('div');
  host.innerHTML = '<p class="skeleton">Carregando…</p>';
  content.replaceChildren(host);
  try {
    const mod = await import('./views/' + view.module + '.js');
    if (seq !== routeSeq) return;
    host.innerHTML = '';
    const result = await mod.render(host, { arg, me: state.me, state, go });
    if (seq !== routeSeq) { if (typeof result === 'function') result(); return; }
    if (typeof result === 'function') cleanup = result;
    content.focus({ preventScroll: true });
  } catch (error) {
    if (seq !== routeSeq) return;
    host.innerHTML = `<div class="card err-card"><h2 class="sec-title">Não foi possível carregar esta tela</h2>
      <p>${esc(error.message || 'Erro inesperado.')}</p><button class="btn blue" type="button" data-action="reload-view">Tentar de novo</button></div>`;
  }
}

function toggleSidebar(force) {
  const sidebar = $('#sidebar');
  const open = force === undefined ? !sidebar.classList.contains('open') : !!force;
  sidebar.classList.toggle('open', open);
  $('#sidebar-scrim').classList.toggle('show', open);
}

export async function enterApp(me) {
  state.me = me;
  $('#view-auth').classList.add('hidden');
  $('#app').classList.remove('hidden');
  buildMenu();
  updateShell();
  window.scrollTo(0, 0);
  refreshAiBadge();
  if (!parseHash().id) history.replaceState(null, '', '#/' + defaultView());
  await route();
}

export function leaveApp(message) {
  state.me = null;
  if (cleanup) { try { cleanup(); } catch (e) { /* ignorado */ } cleanup = null; }
  history.replaceState(null, '', location.pathname);
  $('#app').classList.add('hidden');
  $('#view-auth').classList.remove('hidden');
  showAuth('login', { notice: message });
  window.scrollTo(0, 0);
}

export function refreshShell(patch) {
  if (patch) Object.assign(state.me, patch);
  updateShell();
}

async function logout() {
  try { await api.post('/api/auth/logout', {}, { allow401: true }); } catch (e) { /* encerra mesmo assim */ }
  leaveApp();
}

async function boot() {
  applyStoredTheme();
  installDelegation();
  onClick('toggle-theme', toggleTheme);
  onClick('toggle-sidebar', () => toggleSidebar());
  onClick('logout', logout);
  onClick('reload-view', () => route());
  onClick('scroll-login', () => {
    const card = $('#auth-card');
    card.scrollIntoView({ behavior: 'smooth', block: 'center' });
    const first = card.querySelector('input'); if (first) first.focus();
  });
  window.addEventListener('hashchange', route);
  setSessionExpiredHandler(() => {
    if (state.me) { toast('Sua sessão expirou. Entre novamente.', 'error'); leaveApp('Sua sessão expirou. Entre novamente.'); }
  });

  let status;
  try {
    status = await api.get('/api/setup/status');
  } catch (error) {
    showAuth('offline', { message: error.message });
    return;
  }
  state.orgName = status.orgName;
  if (status.needsSetup) { showAuth('setup'); return; }
  try {
    await enterApp(await api.get('/api/me', { allow401: true }));
  } catch (error) {
    const token = new URLSearchParams(location.search).get('convite');
    showAuth(token ? 'invite' : 'login', { token });
  }
}

boot();
