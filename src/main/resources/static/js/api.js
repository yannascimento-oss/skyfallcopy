/* Cliente da API. Cuida do cookie de sessão (enviado pelo navegador) e do token CSRF:
   o servidor grava o cookie XSRF-TOKEN e exige o mesmo valor no cabeçalho X-XSRF-TOKEN em toda alteração. */

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

let onSessionExpired = () => {};
export function setSessionExpiredHandler(fn) { onSessionExpired = fn; }

function xsrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

async function ensureCsrf() {
  if (!xsrfToken()) await fetch('/api/auth/csrf', { credentials: 'same-origin' });
}

async function send(method, url, { body, form, allow401 = false } = {}, retried = false) {
  const headers = { Accept: 'application/json' };
  const options = { method, credentials: 'same-origin', headers };
  if (method !== 'GET') {
    await ensureCsrf();
    const token = xsrfToken();
    if (token) headers['X-XSRF-TOKEN'] = token;
  }
  if (form) options.body = form;
  else if (body !== undefined) { headers['Content-Type'] = 'application/json'; options.body = JSON.stringify(body); }

  let response;
  try {
    response = await fetch(url, options);
  } catch (e) {
    throw new ApiError(0, 'Não foi possível falar com o servidor. Verifique a conexão e tente de novo.');
  }

  let data = null;
  const text = response.status === 204 ? '' : await response.text();
  if (text) { try { data = JSON.parse(text); } catch (e) { data = null; } }

  if (response.ok) return data;

  // Token de segurança vencido ou ausente: pega um novo e repete uma vez.
  if (response.status === 403 && !retried && data && /Sessão de segurança inválida/.test(data.message || '')) {
    await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    return send(method, url, { body, form, allow401 }, true);
  }
  if (response.status === 401 && !allow401) onSessionExpired();
  const message = (data && data.message) || (response.status >= 500
    ? 'O servidor teve um problema. Tente de novo em instantes.' : 'Não foi possível concluir a operação.');
  throw new ApiError(response.status, message);
}

/** Envio de arquivo com progresso real (fetch não informa o progresso do upload). */
function uploadWithProgress(url, file, onProgress, retried = false) {
  return ensureCsrf().then(() => new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', url);
    xhr.withCredentials = true;
    xhr.setRequestHeader('Accept', 'application/json');
    const token = xsrfToken();
    if (token) xhr.setRequestHeader('X-XSRF-TOKEN', token);
    if (onProgress) xhr.upload.addEventListener('progress', (e) => { if (e.lengthComputable) onProgress(e.loaded / e.total); });
    xhr.addEventListener('error', () => reject(new ApiError(0, 'Não foi possível falar com o servidor. Verifique a conexão e tente de novo.')));
    xhr.addEventListener('load', () => {
      let data = null;
      try { data = xhr.responseText ? JSON.parse(xhr.responseText) : null; } catch (e) { data = null; }
      if (xhr.status >= 200 && xhr.status < 300) { resolve(data); return; }
      if (xhr.status === 403 && !retried && data && /Sessão de segurança inválida/.test(data.message || '')) {
        fetch('/api/auth/csrf', { credentials: 'same-origin' }).then(() => uploadWithProgress(url, file, onProgress, true)).then(resolve, reject);
        return;
      }
      if (xhr.status === 401) onSessionExpired();
      const message = (data && data.message) || (xhr.status === 413 ? 'O arquivo é grande demais.'
        : xhr.status >= 500 ? 'O servidor teve um problema. Tente de novo em instantes.' : 'Não foi possível enviar o arquivo.');
      reject(new ApiError(xhr.status, message));
    });
    const form = new FormData();
    form.append('file', file);
    xhr.send(form);
  }));
}

export const api = {
  get: (url, options) => send('GET', url, options),
  post: (url, body, options) => send('POST', url, { ...options, body }),
  put: (url, body, options) => send('PUT', url, { ...options, body }),
  del: (url, options) => send('DELETE', url, options),
  uploadWithProgress,
  upload: (url, file) => {
    const form = new FormData();
    form.append('file', file);
    return send('POST', url, { form });
  },
};
