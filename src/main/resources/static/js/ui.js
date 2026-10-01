/* Utilitários de interface: texto seguro, avisos, diálogos acessíveis, tema e delegação de eventos.
   Não há onclick inline em nenhum lugar: tudo passa por data-action, o que permite uma política de
   segurança de conteúdo sem 'unsafe-inline' para scripts. */

export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

/** Escapa texto para uso dentro de HTML. */
export function esc(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

export function initials(name) {
  const parts = String(name || '?').trim().split(/\s+/).filter(Boolean);
  const letters = (parts[0] ? parts[0][0] : '?') + (parts.length > 1 ? parts[parts.length - 1][0] : '');
  return letters.toUpperCase();
}

const DATE = new Intl.DateTimeFormat('pt-BR', { timeZone: 'America/Bahia', day: '2-digit', month: '2-digit', year: 'numeric' });
const DATE_TIME = new Intl.DateTimeFormat('pt-BR', { timeZone: 'America/Bahia', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });

export function fmtDate(iso) { return iso ? DATE.format(new Date(iso)) : '—'; }
export function fmtDateTime(iso) { return iso ? DATE_TIME.format(new Date(iso)) : '—'; }

export function fmtBytes(n) {
  const v = Number(n) || 0;
  if (v < 1024) return v + ' B';
  if (v < 1024 * 1024) return (v / 1024).toFixed(1).replace('.', ',') + ' KB';
  if (v < 1024 * 1024 * 1024) return (v / 1024 / 1024).toFixed(1).replace('.', ',') + ' MB';
  return (v / 1024 / 1024 / 1024).toFixed(1).replace('.', ',') + ' GB';
}

/** Custo estimado: a API guarda micro-dólares. */
export function fmtUsd(micro) {
  const usd = (Number(micro) || 0) / 1_000_000;
  return 'US$ ' + usd.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
}

export function fmtDuration(seconds) {
  const s = Number(seconds) || 0;
  const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60);
  return d ? `${d} d ${h} h` : h ? `${h} h ${m} min` : `${m} min`;
}

export function stripHtml(html) {
  const box = document.createElement('div');
  box.innerHTML = html || '';
  return (box.textContent || '').replace(/\s+/g, ' ').trim();
}

export function pct(part, total) { return total ? Math.round((part / total) * 100) : 0; }

export function debounce(fn, ms = 250) {
  let timer = null;
  return (...args) => { clearTimeout(timer); timer = setTimeout(() => fn(...args), ms); };
}

/* ---------------- avisos ---------------- */

export function toast(message, kind = '') {
  const box = document.getElementById('toasts');
  if (!box) return;
  const el = document.createElement('div');
  el.className = 'toast' + (kind ? ' ' + kind : '');
  el.textContent = message;
  box.appendChild(el);
  const ttl = kind === 'error' ? 8000 : 4500;
  setTimeout(() => { el.classList.add('out'); setTimeout(() => el.remove(), 300); }, ttl);
}

/* ---------------- delegação de eventos ---------------- */

const clicks = new Map();
const submits = new Map();
const inputs = new Map();
const changes = new Map();

export const onClick = (name, fn) => clicks.set(name, fn);
export const onSubmit = (name, fn) => submits.set(name, fn);
export const onInput = (name, fn) => inputs.set(name, fn);
export const onChange = (name, fn) => changes.set(name, fn);

export function installDelegation() {
  document.addEventListener('click', (event) => {
    const el = event.target.closest('[data-action]');
    if (!el || el.disabled) return;
    const fn = clicks.get(el.dataset.action);
    if (fn) { event.preventDefault(); fn(el, event); }
  });
  document.addEventListener('submit', (event) => {
    const form = event.target.closest('[data-form]');
    if (!form) return;
    event.preventDefault();
    const fn = submits.get(form.dataset.form);
    if (fn) fn(form, event);
  });
  document.addEventListener('input', (event) => {
    const el = event.target.closest('[data-input]');
    const fn = el && inputs.get(el.dataset.input);
    if (fn) fn(el, event);
  });
  document.addEventListener('change', (event) => {
    const el = event.target.closest('[data-change]');
    const fn = el && changes.get(el.dataset.change);
    if (fn) fn(el, event);
  });
}

/** Desativa um botão e mostra um texto de espera enquanto a ação roda. */
export async function withBusy(button, label, fn) {
  const original = button ? button.textContent : '';
  if (button) { button.disabled = true; if (label) button.textContent = label; }
  try { return await fn(); }
  finally { if (button) { button.disabled = false; button.textContent = original; } }
}

/* ---------------- diálogos ---------------- */

let modalSeq = 0;

/**
 * Abre um diálogo modal acessível (foco preso, Esc fecha, devolve o foco).
 * Cada ação pode ter um handler(api); api.close(valor) fecha e resolve a promessa, api.error(msg) mostra erro,
 * api.busy(true) trava os botões. Sem handler, a ação fecha o diálogo devolvendo o id dela.
 * A promessa resolve com null quando o diálogo é cancelado.
 */
export function openModal({ title, description = '', body = '', actions = [], wide = false, danger = false }) {
  return new Promise((resolve) => {
    const root = document.getElementById('modal-root');
    const returnFocus = document.activeElement;
    const id = 'modal-' + (++modalSeq);
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.innerHTML = `
      <div class="modal${wide ? ' wide' : ''}" role="${danger ? 'alertdialog' : 'dialog'}" aria-modal="true" aria-labelledby="${id}-t"${description ? ` aria-describedby="${id}-d"` : ''}>
        <h3 id="${id}-t">${esc(title)}</h3>
        ${description ? `<p class="d" id="${id}-d">${esc(description)}</p>` : ''}
        <div class="form-error hidden modal-error" role="alert"></div>
        <div class="modal-body">${body}</div>
        <div class="modal-actions">${actions.map((a) =>
          `<button type="button" class="btn ${esc(a.kind || '')}" data-modal-action="${esc(a.id)}">${esc(a.label)}</button>`).join('')}</div>
      </div>`;
    root.appendChild(overlay);
    const modal = overlay.firstElementChild;
    let busy = false;
    let finished = false;

    const finish = (value) => {
      if (finished) return;
      finished = true;
      document.removeEventListener('keydown', onKey, true);
      overlay.remove();
      if (returnFocus && typeof returnFocus.focus === 'function' && document.contains(returnFocus)) returnFocus.focus();
      resolve(value);
    };
    const api = {
      el: modal,
      $: (selector) => modal.querySelector(selector),
      close: (value = null) => finish(value),
      error(message) {
        const box = modal.querySelector('.modal-error');
        box.textContent = message || '';
        box.classList.toggle('hidden', !message);
      },
      busy(on) {
        busy = on;
        modal.querySelectorAll('[data-modal-action]').forEach((b) => { b.disabled = on; });
      },
    };

    const run = async (action) => {
      if (busy) return;
      api.error('');
      if (!action.handler) { finish(action.id); return; }
      await action.handler(api);
    };

    const onKey = (event) => {
      if (overlay !== root.lastElementChild) return;
      if (event.key === 'Escape') { event.preventDefault(); if (!busy) finish(null); return; }
      if (event.key === 'Enter' && event.target.matches('input:not([type=checkbox]):not([type=file])')) {
        const primary = actions.find((a) => a.primary);
        if (primary) { event.preventDefault(); run(primary); }
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = Array.from(modal.querySelectorAll('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href]'));
      if (!focusable.length) return;
      const first = focusable[0], last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', onKey, true);

    overlay.addEventListener('mousedown', (event) => { if (event.target === overlay && !busy) finish(null); });
    modal.querySelectorAll('[data-modal-action]').forEach((button) => {
      // Botões no corpo do diálogo também valem como ação: resolvem a promessa com o id deles.
      button.addEventListener('click', () => run(actions.find((a) => a.id === button.dataset.modalAction) || { id: button.dataset.modalAction }));
    });
    // Foco síncrono: o primeiro campo, ou o botão principal.
    const first = modal.querySelector('input:not([type=hidden]), select, textarea') || modal.querySelector('[data-modal-action]');
    if (first) first.focus();
  });
}

export async function confirmDialog({ title, message, confirmLabel = 'Confirmar', cancelLabel = 'Cancelar', danger = false }) {
  const result = await openModal({
    title, description: message, danger,
    actions: [{ id: 'no', label: cancelLabel }, { id: 'yes', label: confirmLabel, kind: danger ? 'danger' : 'blue', primary: true }],
  });
  return result === 'yes';
}

/** Mostra uma mensagem simples com um botão "Entendi". */
export function infoDialog({ title, message, body = '' }) {
  return openModal({ title, description: message, body, actions: [{ id: 'ok', label: 'Entendi', kind: 'blue', primary: true }] });
}

/* ---------------- tema ---------------- */

const THEME_KEY = 'chatjr.theme';

export function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  document.querySelectorAll('.theme-sun').forEach((e) => e.classList.toggle('hidden', theme === 'dark'));
  document.querySelectorAll('.theme-moon').forEach((e) => e.classList.toggle('hidden', theme !== 'dark'));
}

export function currentTheme() { return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light'; }

export function applyStoredTheme() {
  let stored = null;
  try { stored = window.localStorage.getItem(THEME_KEY); } catch (e) { /* armazenamento indisponível */ }
  const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  applyTheme(stored || (prefersDark ? 'dark' : 'light'));
}

export function toggleTheme() {
  const next = currentTheme() === 'dark' ? 'light' : 'dark';
  applyTheme(next);
  try { window.localStorage.setItem(THEME_KEY, next); } catch (e) { /* armazenamento indisponível */ }
}

/* ---------------- área de transferência ---------------- */

export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch (e) {
    const area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    let ok = false;
    try { ok = document.execCommand('copy'); } catch (err) { ok = false; }
    area.remove();
    return ok;
  }
}
