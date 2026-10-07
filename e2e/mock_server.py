#!/usr/bin/env python3
"""Servidor de mentira do Chat Jr, só para desenvolver e testar a interface sem o backend Java.

Serve a pasta estática e imita o contrato da API (mensagens, status, CSRF por cookie/cabeçalho, sessão por cookie
HttpOnly, cabeçalhos de segurança). NÃO substitui o backend: o contrato real é verificado pelos testes Java e,
no CI, pelo mesmo teste de navegador rodando contra o jar de verdade.
"""
import json, os, re, secrets, sys, threading, time, unicodedata, hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs, unquote
from datetime import datetime, timezone, timedelta

STATIC = os.environ.get('STATIC_DIR') or os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'static')
CSP = ("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; "
       "connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")
MIME = {'.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
        '.png': 'image/png', '.woff2': 'font/woff2', '.svg': 'image/svg+xml', '.txt': 'text/plain; charset=utf-8'}
LOCK = threading.RLock()
DEFAULT_STAGES = ["Resumo Executivo", "Empresa", "Produto/Serviço", "Proposta de Valor", "Mercado", "Público-Alvo", "Concorrentes",
                  "Análise SWOT", "Plano Financeiro", "Riscos", "Metas", "Estratégia de Marketing", "Investimento Inicial",
                  "VPL, TIR e Payback", "Análise de Sensibilidade"]
ALLOWED = {'p', 'b', 'strong', 'ul', 'ol', 'li', 'h4', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'br'}
NOT_IN_PLAN = "<p>Não encontrei essa informação no seu plano. Você pode reformular a pergunta ou falar com a consultoria da Empresa JR.</p>"
NOTHING = "<p>Ainda não há conteúdo liberado no seu plano para eu consultar. Assim que a consultoria publicar as etapas, você poderá fazer perguntas aqui.</p>"


def now(): return datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%S.%f')[:-3] + 'Z'
def slug(s):
    s = unicodedata.normalize('NFD', s); s = ''.join(c for c in s if unicodedata.category(c) != 'Mn')
    return re.sub(r'[^a-z0-9]+', '-', s.lower()).strip('-') or 'etapa'
def norm(s):
    s = unicodedata.normalize('NFD', s or ''); s = ''.join(c for c in s if unicodedata.category(c) != 'Mn')
    return s.lower()
def tokens(s): return {t.rstrip('s') for t in re.findall(r'[a-z0-9]{3,}', norm(s))}
def sanitize(html):
    html = re.sub(r'(?is)<(script|style|iframe|object|embed|svg|math)[^>]*>.*?</\1\s*>', '', html or '')
    html = re.sub(r'(?is)<(script|style)[^>]*>.*$', '', html)
    def keep(m):
        name = m.group(2).lower()
        if name not in ALLOWED: return ''
        return '<br>' if name == 'br' else ('</%s>' % name if m.group(1) else '<%s>' % name)
    html = re.sub(r'<(/?)([a-zA-Z][a-zA-Z0-9]*)[^>]*>', keep, html)
    return re.sub(r'<(?![/a-z])', '&lt;', html)
def plain(html):
    t = re.sub(r'(?i)</(p|li|h4|tr)>|<br\s*/?>', '\n', html or ''); t = re.sub(r'<[^>]*>', '', t)
    return re.sub(r'&amp;', '&', re.sub(r'&lt;', '<', re.sub(r'&gt;', '>', t))).strip()
def esc(t): return t.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


class Store:
    def __init__(self):
        self.reset()
    def reset(self):
        self.seq = 0; self.accounts = {}; self.tabs = {}; self.blocked = set(); self.versions = {}; self.atts = {}
        self.convs = {}; self.msgs = {}; self.queries = []; self.audit = []; self.errors = []; self.calls = []
        self.sessions = {}
        self.requests = []
        self.logo = None
        self.settings = {'setup': False, 'org': None, 'aiKey': None, 'model': 'claude-sonnet-4-6', 'maxTokens': 3000,
                         'temperature': 0.2, 'qph': 60, 'ppd': 20, 'upload': 25}
    def nid(self): self.seq += 1; return self.seq
S = Store()


class ApiErr(Exception):
    def __init__(self, status, msg): self.status = status; self.msg = msg


def audit(actor, action, client=None, tab=None, detail=None):
    S.audit.append({'id': S.nid(), 'actorEmail': actor['email'] if actor else None, 'clientId': client, 'action': action,
                    'tabId': tab['id'] if tab else None, 'tabName': tab['name'] if tab else None, 'detail': detail, 'createdAt': now()})
def summary(a):
    return {'id': a['id'], 'name': a['name'], 'email': a['email'], 'company': a.get('company'), 'segment': a.get('segment'),
            'suspended': a['suspended'], 'passwordSet': bool(a.get('password')), 'lastLoginAt': a.get('lastLogin'), 'planVersion': a['planVersion']}
def tabview(t, html=False, admin=False):
    v = {'id': t['id'], 'slug': t['slug'], 'name': t['name'], 'title': t['title'], 'sortOrder': t['sortOrder'],
         'shortDescription': t['shortDescription'], 'whatIsIt': t['whatIsIt'], 'objective': t['objective'],
         'keyPoints': t['keyPoints'], 'suggestedQuestions': t['suggestedQuestions'], 'contentVersion': t['contentVersion']}
    if t['source']: v['source'] = t['source']
    if t['contentUpdatedAt']: v['contentUpdatedAt'] = t['contentUpdatedAt']
    if html: v['html'] = t['html']
    if admin: v['published'] = t['published']; v['allowed'] = (t['clientId'], t['id']) not in S.blocked
    return {k: x for k, x in v.items() if x is not None}
def new_account(role, name, email, company=None, segment=None):
    if any(a['email'] == email for a in S.accounts.values()): raise ApiErr(409, 'Já existe um acesso com esse e-mail.')
    a = {'id': S.nid(), 'role': role, 'name': name, 'email': email, 'password': None, 'company': company, 'segment': segment,
         'suspended': False, 'planVersion': 0, 'lastLogin': None, 'failed': 0, 'lockedUntil': 0, 'token': None, 'tokenExp': 0}
    S.accounts[a['id']] = a
    if role == 'CLIENT':
        for i, n in enumerate(DEFAULT_STAGES):
            t = {'id': S.nid(), 'clientId': a['id'], 'slug': slug(n), 'name': n, 'title': n, 'sortOrder': i, 'html': '', 'published': False,
                 'contentVersion': 0, 'shortDescription': 'Definição de ' + n, 'whatIsIt': 'O que é ' + n, 'objective': 'Objetivo de ' + n,
                 'keyPoints': [], 'suggestedQuestions': [], 'source': None, 'contentUpdatedAt': None}
            S.tabs[t['id']] = t
    return a
def invite(a):
    a['token'] = secrets.token_urlsafe(32); a['tokenExp'] = time.time() + 48 * 3600
    return {'client': summary(a), 'inviteToken': a['token'], 'invitePath': '/?convite=' + a['token'], 'expiresAt': now()}
def policy(pw, email):
    if len(pw) < 10: return 'A senha precisa ter pelo menos 10 caracteres.'
    if not re.search(r'[A-Za-z]', pw) or not re.search(r'\d', pw): return 'A senha precisa misturar letras e números.'
    if pw.lower() == email.lower(): return 'A senha não pode ser igual ao e-mail.'
    return None
def mask(k): return '••••' if not k or len(k) <= 11 else k[:7] + '…' + k[-4:]
def visible_tabs(cid): return [t for t in sorted(S.tabs.values(), key=lambda t: (t['sortOrder'], t['id'])) if t['clientId'] == cid and t['published'] and (cid, t['id']) not in S.blocked]
def snapshot(t, reason, who):
    S.versions.setdefault(t['id'], []).append({'version': t['contentVersion'], 'reason': reason, 'createdBy': who, 'createdAt': now(), 'title': t['title'],
                                                'html': t['html'], 'shortDescription': t['shortDescription'], 'whatIsIt': t['whatIsIt'], 'objective': t['objective'],
                                                'keyPoints': t['keyPoints'], 'suggestedQuestions': t['suggestedQuestions']})
def apply_content(t, who, reason, c):
    snapshot(t, reason, who['email'] if who else None)
    t.update(c); t['contentVersion'] += 1; t['contentUpdatedAt'] = now()
    S.accounts[t['clientId']]['planVersion'] += 1


def pdf_text(data):
    parts = re.findall(rb'\(((?:\\.|[^\\)])*)\)\s*Tj', data)
    return '\n'.join(p.decode('latin-1').replace('\\(', '(').replace('\\)', ')') for p in parts).strip()
def parse_multipart(headers, body):
    ctype = headers.get('Content-Type', ''); m = re.search(r'boundary=(.+)', ctype)
    if not m: return None, None
    b = ('--' + m.group(1).strip('"')).encode()
    for part in body.split(b):
        if b'filename=' in part:
            head, _, data = part.partition(b'\r\n\r\n'); fn = re.search(rb'filename="([^"]*)"', head)
            return (fn.group(1).decode('utf-8', 'replace') if fn else 'plano.pdf'), data.rstrip(b'\r\n-')
    return None, None
def att_view(tab_id):
    a = S.atts.get(tab_id)
    if not a: return {'tabId': tab_id, 'hasPdf': False, 'pdfPages': 0, 'pdfChars': 0, 'storedBytes': 0, 'state': 'IDLE', 'processed': False, 'extractedSections': 0, 'sections': []}
    return {'tabId': tab_id, 'hasPdf': bool(a.get('text')), 'pdfName': a.get('name'), 'pdfPages': a.get('pages', 0), 'pdfChars': len(a.get('text') or ''),
            'storedBytes': a.get('bytes', 0), 'slideLink': a.get('link'), 'state': a.get('state', 'IDLE'), 'processed': a.get('processed', False),
            'extractedSections': len(a.get('sections', [])), 'sections': a.get('sections', []), 'processedAt': a.get('processedAt'), 'message': a.get('message')}
def run_processing(tab_id, who):
    time.sleep(0.8)
    with LOCK:
        a = S.atts.get(tab_id); t = S.tabs.get(tab_id)
        if not a or not t: return
        text = a['text']; paras = [p.strip() for p in re.split(r'\n+', text) if p.strip()]
        html = ''.join('<p>%s</p>' % esc(p) for p in paras); sections = [p for p in paras if len(p) < 60][:5]
        note = None if S.settings['aiKey'] else 'Processado sem IA: nenhuma chave da IA está configurada. O conteúdo foi extraído do PDF sem reescrita.'
        if S.settings['aiKey']: S.calls.append({'at': now(), 'kind': 'PROCESS', 'status': 'OK', 'httpStatus': 200, 'durationMs': 800, 'inputTokens': 120, 'outputTokens': 80, 'model': S.settings['model'], 'error': None, 'estCostMicroUsd': 1560})
        apply_content(t, who, 'PROCESS', {'html': html, 'source': a['name']})
        a.update({'state': 'DONE', 'processed': True, 'sections': sections, 'processedAt': now(), 'message': note})
        audit(who, 'TAB_PROCESSED', t['clientId'], t)


def chat_answer(acc, conv_id, q):
    q = ' '.join(q.split())
    if not q: raise ApiErr(400, 'Digite sua pergunta.')
    if len(q) > 500: raise ApiErr(400, 'A pergunta pode ter no máximo 500 caracteres.')
    hour = time.time() - 3600
    if len([x for x in S.queries if x['clientId'] == acc['id'] and x['ts'] > hour]) >= S.settings['qph']:
        raise ApiErr(429, 'Você atingiu o limite de %d perguntas por hora. Tente de novo em alguns minutos.' % S.settings['qph'])
    if conv_id is not None and not (conv_id in S.convs and S.convs[conv_id]['clientId'] == acc['id']): raise ApiErr(404, 'Conversa não encontrada.')
    tabs = visible_tabs(acc['id']); qt = tokens(q); best = None
    for t in tabs:
        for para in [p for p in plain(t['html']).split('\n') if p.strip()]:
            sc = len(qt & tokens(para + ' ' + t['name']))
            if sc and (best is None or sc > best[0]): best = (sc, t, para)
    answered, degraded, source, tab_id = False, False, None, None
    if not tabs: html = NOTHING
    elif not best: html = NOT_IN_PLAN
    else:
        _, t, para = best; answered = True; source = t['name']; tab_id = t['id']
        if S.settings['aiKey']:
            html = '<p>%s</p>' % esc(para); S.calls.append({'at': now(), 'kind': 'CHAT', 'status': 'OK', 'httpStatus': 200, 'durationMs': 300, 'inputTokens': 200, 'outputTokens': 60, 'model': S.settings['model'], 'error': None, 'estCostMicroUsd': 1500})
        else:
            degraded = True
            html = '<p>Não consegui usar a inteligência artificial agora, mas estes trechos do seu plano parecem responder à sua pergunta:</p><p><b>%s</b>: %s</p>' % (esc(t['name']), esc(para[:350]))
    ts = now()
    if conv_id is None:
        conv_id = S.nid(); S.convs[conv_id] = {'id': conv_id, 'clientId': acc['id'], 'title': q[:60], 'updatedAt': ts}; S.msgs[conv_id] = []
    c = S.convs[conv_id]; c['updatedAt'] = ts
    S.msgs[conv_id].append({'id': S.nid(), 'role': 'USER', 'html': esc(q), 'source': None, 'inference': False, 'degraded': False, 'createdAt': ts})
    mid = S.nid()
    S.msgs[conv_id].append({'id': mid, 'role': 'AI', 'html': html, 'source': source, 'inference': False, 'degraded': degraded, 'createdAt': ts})
    S.queries.append({'clientId': acc['id'], 'q': q, 'type': 'INFORMACAO', 'answered': answered, 'source': source, 'theme': source, 'degraded': degraded, 'ts': time.time(), 'at': ts})
    return {'conversationId': conv_id, 'messageId': mid, 'html': html, 'source': source, 'sourceTabId': tab_id, 'inference': False, 'degraded': degraded, 'answered': answered, 'createdAt': ts}


def indicators(entries, days):
    total = len(entries); ans = sum(1 for e in entries if e['answered'])
    src, theme, freq = {}, {}, {}
    for e in entries:
        if e['answered'] and e['source']: src[e['source']] = src.get(e['source'], 0) + 1
        if e['answered'] and e['theme']: theme[e['theme']] = theme.get(e['theme'], 0) + 1
        k = re.sub(r'[^a-z0-9]+', ' ', norm(e['q'])).strip(); freq.setdefault(k, [e['q'], 0])[1] += 1
    top = lambda d: [{'label': k, 'count': v} for k, v in sorted(d.items(), key=lambda x: (-x[1], x[0]))[:8]]
    today = datetime.now(timezone.utc).date(); per = []
    for i in range(min(days, 14) - 1, -1, -1):
        d = today - timedelta(days=i); per.append({'date': d.isoformat(), 'count': sum(1 for e in entries if e['at'][:10] == d.isoformat())})
    return {'total': total, 'answered': ans, 'unanswered': total - ans, 'answeredPercent': round(ans * 100 / total) if total else 0,
            'degraded': sum(1 for e in entries if e['degraded']), 'byType': {'INFORMACAO': total, 'DUVIDA': 0, 'INTERPRETACAO': 0, 'DECISAO': 0},
            'topSources': top(src), 'topThemes': top(theme), 'frequentQuestions': [{'label': v[0], 'count': v[1]} for v in sorted(freq.values(), key=lambda x: -x[1]) if v[1] >= 2][:8],
            'unansweredQuestions': [{'question': e['q'], 'createdAt': e['at']} for e in reversed(entries) if not e['answered']][:20],
            'perDay': per, 'lastQuestionAt': entries[-1]['at'] if entries else None, 'windowDays': days}


class Handler(BaseHTTPRequestHandler):
    server_version = 'mock'
    def log_message(self, *a): pass

    def send_json(self, status, data=None, cookies=()):
        ctype = 'application/json'
        if isinstance(data, tuple) and data[0] == 'RAW':
            body, ctype = data[1], data[2]
        else:
            body = b'' if data is None else json.dumps(data, ensure_ascii=False).encode()
        self.send_response(status)
        if body: self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(body)))
        for c in cookies: self.send_header('Set-Cookie', c)
        self.security_headers(); self.end_headers()
        if body: self.wfile.write(body)

    def security_headers(self):
        self.send_header('Content-Security-Policy', CSP); self.send_header('X-Frame-Options', 'DENY')
        self.send_header('X-Content-Type-Options', 'nosniff'); self.send_header('Referrer-Policy', 'same-origin')

    def cookies(self):
        out = {}
        for part in (self.headers.get('Cookie') or '').split(';'):
            if '=' in part: k, v = part.strip().split('=', 1); out[k] = v
        return out

    def do_GET(self): self.dispatch('GET')
    def do_POST(self): self.dispatch('POST')
    def do_PUT(self): self.dispatch('PUT')
    def do_DELETE(self): self.dispatch('DELETE')

    def dispatch(self, method):
        url = urlparse(self.path); path = url.path
        if not path.startswith('/api/') and not path.startswith('/actuator'):
            return self.static(path)
        n = int(self.headers.get('Content-Length') or 0); raw = self.rfile.read(n) if n else b''
        ck = self.cookies(); set_cookies = []
        if 'XSRF-TOKEN' not in ck:
            ck['XSRF-TOKEN'] = secrets.token_urlsafe(24); set_cookies.append('XSRF-TOKEN=%s; Path=/; SameSite=Strict; Secure' % ck['XSRF-TOKEN'])
        try:
            if method != 'GET':
                if not self.headers.get('X-XSRF-TOKEN') or self.headers.get('X-XSRF-TOKEN') != ck['XSRF-TOKEN']:
                    raise ApiErr(403, 'Sessão de segurança inválida. Recarregue a página e tente de novo.')
            with LOCK:
                status, data, extra = self.route(method, path, parse_qs(url.query), raw, ck)
            self.send_json(status, data, set_cookies + extra)
        except ApiErr as e:
            self.send_json(e.status, {'message': e.msg}, set_cookies)
        except Exception as e:  # erro do próprio mock
            import traceback; traceback.print_exc()
            self.send_json(500, {'message': 'Erro no servidor de mentira: %s' % e}, set_cookies)

    def static(self, path):
        if path == '/': path = '/index.html'
        full = os.path.normpath(os.path.join(STATIC, unquote(path).lstrip('/')))
        if not full.startswith(os.path.normpath(STATIC)) or not os.path.isfile(full):
            self.send_response(404); self.security_headers(); self.send_header('Content-Length', '0'); self.end_headers(); return
        data = open(full, 'rb').read()
        self.send_response(200); self.send_header('Content-Type', MIME.get(os.path.splitext(full)[1], 'application/octet-stream'))
        self.send_header('Content-Length', str(len(data))); self.send_header('Cache-Control', 'no-store'); self.security_headers(); self.end_headers(); self.wfile.write(data)

    # ---------------- rotas da API ----------------
    def route(self, method, path, q, raw, ck):
        j = {}
        if raw and 'multipart' not in (self.headers.get('Content-Type') or ''):
            try: j = json.loads(raw or b'{}')
            except ValueError: raise ApiErr(400, 'A requisição está incompleta ou mal formada.')
        sid = ck.get('JSESSIONID'); me = S.accounts.get(S.sessions.get(sid)) if sid else None
        if me and me['suspended']:
            S.sessions.pop(sid, None); raise ApiErr(401, 'Este acesso está suspenso. Fale com a consultoria da Empresa JR.')
        def need(*roles):
            if not me: raise ApiErr(401, 'Sua sessão expirou ou não foi iniciada. Entre novamente.')
            if roles and me['role'] not in roles: raise ApiErr(403, 'Você não tem permissão para esta ação.')
        m = lambda pat: re.fullmatch(pat, path)
        R = lambda status=200, data=None, c=(): (status, data, list(c))

        if path == '/actuator/health': return R(200, {'status': 'UP'})
        if path == '/api/auth/csrf': return R(204)
        if path == '/api/setup/status': return R(200, {'needsSetup': not S.settings['setup'], 'orgName': S.settings['org'], 'hasLogo': S.logo is not None})
        if path == '/api/public/logo':
            if S.logo is None: raise ApiErr(404, 'Recurso não encontrado.')
            return R(200, ('RAW', S.logo, 'image/png'))
        if path == '/api/access-requests' and method == 'POST':
            if (j.get('website') or '').strip(): return R(202)
            if not (j.get('name') or '').strip() or not (j.get('company') or '').strip(): raise ApiErr(400, 'Informe seu nome.')
            if not re.fullmatch(r'[^@\s]+@[^@\s]+\.[^@\s]+', j.get('email') or ''): raise ApiErr(400, 'Informe um e-mail válido.')
            em = j['email'].strip().lower()
            if not any(r['email'] == em and r['open'] for r in S.requests):
                S.requests.append({'id': S.nid(), 'name': j['name'].strip(), 'email': em, 'company': j['company'].strip(), 'phone': j.get('phone'), 'message': j.get('message'), 'createdAt': now(), 'open': True})
            return R(202)
        if path == '/api/setup' and method == 'POST':
            if S.settings['setup']: raise ApiErr(409, 'A instalação inicial já foi concluída.')
            p = policy(j.get('password', ''), j.get('adminEmail', ''))
            if p: raise ApiErr(400, p)
            a = new_account('ADMIN', j['adminName'].strip(), j['adminEmail'].strip().lower()); a['password'] = j['password']
            S.settings.update(setup=True, org=j['orgName'].strip(), aiKey=(j.get('aiKey') or None)); audit(a, 'SETUP'); return R(201)
        if path == '/api/auth/login' and method == 'POST':
            if not S.settings['setup']: raise ApiErr(409, 'A instalação inicial ainda não foi concluída.')
            a = next((x for x in S.accounts.values() if x['email'] == j.get('email', '').strip().lower()), None)
            if a and a['lockedUntil'] > time.time(): raise ApiErr(429, 'Muitas tentativas incorretas. Tente novamente em 15 minuto(s).')
            if not a or not a.get('password') or a['password'] != j.get('password'):
                if a:
                    a['failed'] += 1
                    if a['failed'] >= 5: a['lockedUntil'] = time.time() + 900
                raise ApiErr(401, 'E-mail ou senha incorretos.')
            if a['suspended']: raise ApiErr(403, 'Este acesso está suspenso. Fale com a consultoria da Empresa JR.')
            a['failed'] = 0; a['lastLogin'] = now(); new = secrets.token_hex(16); S.sessions[new] = a['id']; audit(a, 'LOGIN', a['id'])
            return R(200, self.me_view(a), ['JSESSIONID=%s; Path=/; HttpOnly; SameSite=Strict; Secure' % new])
        if path == '/api/auth/logout': S.sessions.pop(sid, None); return R(204)
        if path == '/api/auth/accept-invite' and method == 'POST':
            a = next((x for x in S.accounts.values() if x['token'] and x['token'] == j.get('token') and x['tokenExp'] > time.time()), None)
            if not a: raise ApiErr(400, 'Este link é inválido ou expirou. Peça um novo à consultoria da Empresa JR.')
            p = policy(j.get('password', ''), a['email'])
            if p: raise ApiErr(400, p)
            a['password'] = j['password']; a['token'] = None; a['failed'] = 0; a['lockedUntil'] = 0; return R(204)
        if path == '/api/me':
            need(); return R(200, self.me_view(me))
        if path == '/api/me/password' and method == 'POST':
            need()
            if me['password'] != j.get('currentPassword'): raise ApiErr(400, 'A senha atual está incorreta.')
            p = policy(j.get('newPassword', ''), me['email'])
            if p: raise ApiErr(400, p)
            if j['newPassword'] == me['password']: raise ApiErr(400, 'A nova senha precisa ser diferente da atual.')
            me['password'] = j['newPassword']; audit(me, 'PASSWORD_CHANGED', me['id']); return R(204)

        # ---- plano (cliente e consultoria)
        g = m(r'/api/clients/(\d+)/tabs')
        if g and method == 'GET':
            need(); cid = int(g.group(1)); self.check_client(me, cid)
            if me['role'] == 'ADMIN': return R(200, [tabview(t, admin=True) for t in sorted((t for t in S.tabs.values() if t['clientId'] == cid), key=lambda t: (t['sortOrder'], t['id']))])
            return R(200, [tabview(t) for t in visible_tabs(cid)])
        g = m(r'/api/clients/(\d+)/tabs/(\d+)')
        if g and method == 'GET':
            need(); cid, tid = int(g.group(1)), int(g.group(2)); self.check_client(me, cid)
            t = S.tabs.get(tid)
            if not t or t['clientId'] != cid or (me['role'] != 'ADMIN' and (not t['published'] or (cid, tid) in S.blocked)): raise ApiErr(404, 'Etapa não encontrada.')
            return R(200, tabview(t, html=True, admin=me['role'] == 'ADMIN'))
        g = m(r'/api/clients/(\d+)/progress')
        if g:
            need(); cid = int(g.group(1)); self.check_client(me, cid)
            allowed = [t for t in S.tabs.values() if t['clientId'] == cid and (cid, t['id']) not in S.blocked]
            return R(200, {'total': len(allowed), 'available': sum(1 for t in allowed if t['published'])})
        g = m(r'/api/clients/(\d+)/export\.(json|pdf)')
        if g: raise ApiErr(501, 'Exportação não é simulada no servidor de mentira.')

        # ---- chat
        if path == '/api/chat' and method == 'POST':
            need('CLIENT'); return R(200, chat_answer(me, j.get('conversationId'), j.get('question') or ''))
        if path == '/api/chat/conversations':
            need('CLIENT'); return R(200, sorted([{'id': c['id'], 'title': c['title'], 'updatedAt': c['updatedAt']} for c in S.convs.values() if c['clientId'] == me['id']], key=lambda c: -c['id']))
        g = m(r'/api/chat/conversations/(\d+)(/messages)?')
        if g:
            need('CLIENT'); cid = int(g.group(1)); c = S.convs.get(cid)
            if not c or c['clientId'] != me['id']: raise ApiErr(404, 'Conversa não encontrada.')
            if method == 'DELETE': S.convs.pop(cid); S.msgs.pop(cid, None); return R(204)
            return R(200, S.msgs[cid])
        if path == '/api/chat/indicators':
            need('CLIENT'); return R(200, indicators([x for x in S.queries if x['clientId'] == me['id']], int(q.get('days', ['30'])[0])))

        # ---- administração
        if path.startswith('/api/admin/'): need('ADMIN')
        if path == '/api/admin/access-requests': return R(200, [{k: r[k] for k in ('id', 'name', 'email', 'company', 'phone', 'message', 'createdAt')} for r in S.requests if r['open']])
        if path == '/api/admin/access-requests/count': return R(200, {'open': sum(1 for r in S.requests if r['open'])})
        g = m(r'/api/admin/access-requests/(\d+)/close')
        if g:
            r = next((x for x in S.requests if x['id'] == int(g.group(1))), None)
            if not r: raise ApiErr(404, 'Pedido não encontrado.')
            r['open'] = False; audit(me, 'ACCESS_REQUEST_CLOSED', None, None, r['email']); return R(204)
        if path == '/api/admin/settings/logo':
            if method == 'DELETE': S.logo = None; return R(204)
            name, data = parse_multipart(self.headers, raw)
            if not data or not data.startswith(b'\x89PNG'): raise ApiErr(400, 'Envie o logo em PNG, JPEG ou WebP.')
            S.logo = data; audit(me, 'LOGO_CHANGED'); return R(204)
        g = m(r'/api/admin/clients/(\d+)/promote')
        if g:
            a = self.client(int(g.group(1))); a['role'] = 'ADMIN'; audit(me, 'ADMIN_PROMOTED', a['id'], None, a['email']); return R(200, summary(a))
        g = m(r'/api/admin/clients/(\d+)/tabs/import')
        if g:
            cid = int(g.group(1)); self.client(cid); tabs_in = j.get('tabs') or []
            if not tabs_in: raise ApiErr(400, 'O arquivo não tem nenhuma etapa.')
            created = 0
            for ti in tabs_in:
                name = (ti.get('name') or '').strip()
                if not name: raise ApiErr(400, 'Toda etapa do arquivo precisa de nome.')
                t = next((x for x in S.tabs.values() if x['clientId'] == cid and x['name'].lower() == name.lower()), None)
                if not t:
                    created += 1
                    t = {'id': S.nid(), 'clientId': cid, 'slug': slug(name), 'name': name, 'title': name, 'sortOrder': 99, 'html': '', 'published': False, 'contentVersion': 0, 'shortDescription': None, 'whatIsIt': None, 'objective': None, 'keyPoints': [], 'suggestedQuestions': [], 'source': None, 'contentUpdatedAt': None}
                    S.tabs[t['id']] = t
                apply_content(t, me, 'EDIT', {'title': ti.get('title') or name, 'html': sanitize(ti.get('html') or ''), 'shortDescription': ti.get('shortDescription'), 'whatIsIt': ti.get('whatIsIt'), 'objective': ti.get('objective'), 'keyPoints': ti.get('keyPoints') or [], 'suggestedQuestions': ti.get('suggestedQuestions') or [], 'source': ti.get('source')})
            audit(me, 'PLAN_IMPORTED', cid); return R(200, {'imported': len(tabs_in), 'created': created})
        if path == '/api/admin/clients' and method == 'GET': return R(200, [summary(a) for a in S.accounts.values() if a['role'] == 'CLIENT'])
        if path == '/api/admin/clients' and method == 'POST':
            a = new_account('CLIENT', j['name'].strip(), j['email'].strip().lower(), j['company'].strip(), (j.get('segment') or None)); audit(me, 'CLIENT_CREATED', a['id'], None, a['email']); return R(201, invite(a))
        g = m(r'/api/admin/clients/(\d+)')
        if g and method == 'PUT':
            a = self.client(int(g.group(1))); a.update(name=j['name'].strip(), company=j['company'].strip(), segment=j.get('segment')); return R(200, summary(a))
        if g and method == 'DELETE':
            a = self.client(int(g.group(1)))
            if (q.get('confirmEmail', [''])[0] or '').strip().lower() != a['email']: raise ApiErr(400, 'Para excluir, confirme digitando o e-mail do cliente: ' + a['email'])
            S.accounts.pop(a['id']); [S.tabs.pop(t) for t in [t['id'] for t in list(S.tabs.values()) if t['clientId'] == a['id']]]; audit(me, 'CLIENT_DELETED', a['id'], None, a['email']); return R(204)
        g = m(r'/api/admin/clients/(\d+)/(invite|suspend|activate)')
        if g:
            a = self.client(int(g.group(1))); act = g.group(2)
            if act == 'invite': return R(200, invite(a))
            a['suspended'] = act == 'suspend'; audit(me, 'ACCESS_SUSPENDED' if a['suspended'] else 'ACCESS_RESTORED', a['id'], None, a['email']); return R(204)
        g = m(r'/api/admin/clients/(\d+)/indicators')
        if g:
            cid = int(g.group(1)); self.client(cid); return R(200, indicators([x for x in S.queries if x['clientId'] == cid], int(q.get('days', ['30'])[0])))
        if path == '/api/admin/indicators': return R(200, indicators(S.queries, int(q.get('days', ['30'])[0])))

        g = m(r'/api/admin/clients/(\d+)/tabs')
        if g and method == 'POST':
            cid = int(g.group(1)); self.client(cid); name = j['name'].strip(); sl, n = slug(name), 2
            while any(t['clientId'] == cid and t['slug'] == sl for t in S.tabs.values()): sl = '%s-%d' % (slug(name), n); n += 1
            t = {'id': S.nid(), 'clientId': cid, 'slug': sl, 'name': name, 'title': name, 'sortOrder': len([1 for t in S.tabs.values() if t['clientId'] == cid]), 'html': '', 'published': False,
                 'contentVersion': 0, 'shortDescription': None, 'whatIsIt': None, 'objective': None, 'keyPoints': [], 'suggestedQuestions': [], 'source': None, 'contentUpdatedAt': None}
            S.tabs[t['id']] = t; audit(me, 'TAB_CREATED', cid, t); return R(201, tabview(t, True, True))
        g = m(r'/api/admin/clients/(\d+)/tabs/(\d+)')
        if g:
            cid, tid = int(g.group(1)), int(g.group(2)); self.client(cid); t = self.tab(cid, tid)
            if method == 'DELETE': S.tabs.pop(tid); audit(me, 'TAB_DELETED', cid, t); return R(204)
            if method == 'PUT':
                if not (j.get('title') or '').strip(): raise ApiErr(400, 'Informe o título da etapa.')
                if len(j.get('keyPoints') or []) > 8: raise ApiErr(400, 'Use no máximo 8 pontos principais.')
                c = {'title': j['title'].strip(), 'html': sanitize(j.get('html') or ''), 'shortDescription': j.get('shortDescription'), 'whatIsIt': j.get('whatIsIt'),
                     'objective': j.get('objective'), 'keyPoints': [x.strip() for x in (j.get('keyPoints') or []) if x.strip()],
                     'suggestedQuestions': [x.strip() for x in (j.get('suggestedQuestions') or []) if x.strip()], 'source': j.get('source')}
                if any(t[k] != v for k, v in c.items()): apply_content(t, me, 'EDIT', c); audit(me, 'TAB_UPDATED', cid, t)
                if j.get('published') is not None and j['published'] != t['published']:
                    t['published'] = j['published']; audit(me, 'TAB_PUBLISHED' if t['published'] else 'TAB_UNPUBLISHED', cid, t)
                return R(200, tabview(t, True, True))
        g = m(r'/api/admin/clients/(\d+)/tabs/(\d+)/scope')
        if g:
            cid, tid = int(g.group(1)), int(g.group(2)); t = self.tab(cid, tid)
            (S.blocked.discard if j.get('allowed') else S.blocked.add)((cid, tid)); audit(me, 'SCOPE_CHANGED', cid, t, 'liberada' if j.get('allowed') else 'bloqueada'); return R(204)
        g = m(r'/api/admin/clients/(\d+)/tabs/(\d+)/versions')
        if g: self.tab(int(g.group(1)), int(g.group(2))); return R(200, [{k: v[k] for k in ('version', 'reason', 'createdBy', 'createdAt', 'title')} for v in reversed(S.versions.get(int(g.group(2)), []))])
        g = m(r'/api/admin/clients/(\d+)/tabs/(\d+)/versions/(\d+)/restore')
        if g:
            cid, tid, ver = int(g.group(1)), int(g.group(2)), int(g.group(3)); t = self.tab(cid, tid)
            old = next((v for v in S.versions.get(tid, []) if v['version'] == ver), None)
            if not old: raise ApiErr(404, 'Versão não encontrada.')
            apply_content(t, me, 'RESTORE', {k: old[k] for k in ('title', 'html', 'shortDescription', 'whatIsIt', 'objective', 'keyPoints', 'suggestedQuestions')}); return R(200, tabview(t, True, True))
        g = m(r'/api/admin/clients/(\d+)/tabs/(\d+)/attachment(/link|/process)?')
        if g:
            cid, tid, sub = int(g.group(1)), int(g.group(2)), g.group(3); t = self.tab(cid, tid); a = S.atts.setdefault(tid, {})
            if sub == '/link':
                link = (j.get('slideLink') or '').strip()
                if link and not re.match(r'https://[^\s/]+', link): raise ApiErr(400, 'Informe um link válido que comece com https://')
                a['link'] = link or None; return R(200, att_view(tid))
            if sub == '/process':
                if not a.get('text'): raise ApiErr(400, 'Envie um PDF antes de processar.')
                if a.get('state') == 'PROCESSING': raise ApiErr(409, 'Esta etapa já está sendo processada.')
                a['state'] = 'PROCESSING'; a['message'] = None; threading.Thread(target=run_processing, args=(tid, me), daemon=True).start(); return R(202, att_view(tid))
            if method == 'DELETE':
                if a.get('state') == 'PROCESSING': raise ApiErr(409, 'Esta etapa está sendo processada. Aguarde terminar para remover o PDF.')
                S.atts.pop(tid, None); return R(204)
            if method == 'POST':
                name, data = parse_multipart(self.headers, raw)
                if data is None or not data.startswith(b'%PDF-'): raise ApiErr(400, 'O arquivo não parece ser um PDF.')
                if len(data) > S.settings['upload'] * 1024 * 1024: raise ApiErr(413, 'O arquivo passa do limite de %d MB.' % S.settings['upload'])
                text = pdf_text(data)
                if not text: raise ApiErr(400, 'Não encontramos texto neste PDF. Se ele for um documento escaneado (imagem), envie uma versão com texto selecionável.')
                a.update(name=name, text=text, pages=max(1, data.count(b'/Type /Page') - data.count(b'/Type /Pages')), bytes=len(data), state='IDLE', processed=False, sections=[], processedAt=None, message=None)
                audit(me, 'PDF_UPLOADED', cid, t, name); return R(200, att_view(tid))
            return R(200, att_view(tid))

        if path == '/api/admin/settings':
            s = S.settings
            return R(200, {'organization': {'name': s['org']}, 'ai': self.ai_view(), 'limits': {'questionsPerHour': s['qph'], 'processingsPerDay': s['ppd'], 'maxUploadMb': s['upload']}})
        if path == '/api/admin/settings/ai' and method == 'PUT':
            if j.get('apiKey'):
                if not j['apiKey'].startswith('sk-ant-') or len(j['apiKey']) < 20: raise ApiErr(400, 'Essa não parece ser uma chave da Anthropic (ela começa com sk-ant-).')
                S.settings['aiKey'] = j['apiKey']
            if j.get('model'):
                if not re.fullmatch(r'[A-Za-z0-9._-]{3,80}', j['model']): raise ApiErr(400, 'Nome de modelo inválido.')
                S.settings['model'] = j['model']
            if j.get('maxTokens') is not None:
                if not 256 <= j['maxTokens'] <= 16000: raise ApiErr(400, 'O mínimo de tokens por resposta é 256.')
                S.settings['maxTokens'] = j['maxTokens']
            if j.get('temperature') is not None: S.settings['temperature'] = j['temperature']
            audit(me, 'AI_SETTINGS_CHANGED'); return R(200, self.ai_view())
        if path == '/api/admin/settings/ai/key' and method == 'DELETE': S.settings['aiKey'] = None; return R(200, self.ai_view())
        if path == '/api/admin/settings/ai/test':
            if not S.settings['aiKey']: raise ApiErr(400, 'Nenhuma chave da IA está configurada.')
            return R(200, {'ok': True, 'message': 'Conexão com a IA funcionando.', 'durationMs': 42, 'model': S.settings['model']})
        if path == '/api/admin/settings/limits' and method == 'PUT':
            for k, lo, hi, msg in (('questionsPerHour', 1, 1000, 'O limite de perguntas por hora deve ser pelo menos 1.'), ('processingsPerDay', 1, 200, 'O limite de processamentos por dia deve ser pelo menos 1.'), ('maxUploadMb', 1, 25, 'O tamanho máximo de upload é 25 MB.')):
                if not lo <= int(j.get(k, 0)) <= hi: raise ApiErr(400, msg)
            S.settings.update(qph=j['questionsPerHour'], ppd=j['processingsPerDay'], upload=j['maxUploadMb']); return R(200, {'questionsPerHour': j['questionsPerHour'], 'processingsPerDay': j['processingsPerDay'], 'maxUploadMb': j['maxUploadMb']})
        if path == '/api/admin/settings/organization' and method == 'PUT':
            if not (j.get('name') or '').strip(): raise ApiErr(400, 'Informe o nome da consultoria.')
            S.settings['org'] = j['name'].strip(); return R(200, {'name': S.settings['org']})
        if path == '/api/admin/admins' and method == 'GET': return R(200, [summary(a) for a in S.accounts.values() if a['role'] == 'ADMIN'])
        if path == '/api/admin/admins' and method == 'POST': return R(201, invite(new_account('ADMIN', j['name'].strip(), j['email'].strip().lower())))
        g = m(r'/api/admin/admins/(\d+)/(invite|suspend|activate)')
        if g:
            a = S.accounts.get(int(g.group(1)))
            if not a or a['role'] != 'ADMIN': raise ApiErr(404, 'Administrador não encontrado.')
            if g.group(2) == 'invite': return R(200, invite(a))
            if g.group(2) == 'suspend' and a['id'] == me['id']: raise ApiErr(400, 'Você não pode suspender o seu próprio acesso.')
            a['suspended'] = g.group(2) == 'suspend'; return R(204)
        if path == '/api/admin/system':
            return R(200, {'version': 'mock', 'javaVersion': 'n/a', 'database': 'Mock em memória', 'uptimeSeconds': 120, 'disk': {'attachmentsBytes': sum(a.get('bytes', 0) for a in S.atts.values()), 'freeBytes': 10**10, 'totalBytes': 10**11},
                           'counts': {'clients': sum(1 for a in S.accounts.values() if a['role'] == 'CLIENT'), 'admins': sum(1 for a in S.accounts.values() if a['role'] == 'ADMIN'), 'tabs': len(S.tabs), 'conversations': len(S.convs), 'questions': len(S.queries)},
                           'ai': {'keyConfigured': bool(S.settings['aiKey']), 'model': S.settings['model'], 'calls30d': len(S.calls), 'errors30d': 0, 'inputTokens30d': sum(c['inputTokens'] for c in S.calls), 'outputTokens30d': sum(c['outputTokens'] for c in S.calls), 'estCostMicroUsd30d': sum(c['inputTokens'] * 3 + c['outputTokens'] * 15 for c in S.calls)},
                           'emailConfigured': False, 'recentErrors': S.errors[-20:], 'recentCalls': list(reversed(S.calls[-20:]))})
        if path == '/api/admin/system/test-email': return R(200, {'ok': False, 'message': 'O envio de e-mail não está configurado nesta instalação. Os convites são entregues por link, que a consultoria copia e envia ao cliente.'})
        if path == '/api/admin/audit':
            items = list(reversed(S.audit)); cid = q.get('clientId', [''])[0]; act = q.get('action', [''])[0]
            if cid: items = [x for x in items if str(x['clientId']) == cid]
            if act: items = [x for x in items if x['action'] == act]
            page, size = int(q.get('page', ['0'])[0]), min(100, int(q.get('size', ['25'])[0]))
            names = {a['id']: a.get('company') or a['name'] for a in S.accounts.values()}
            return R(200, {'items': [dict(x, clientName=names.get(x['clientId'])) for x in items[page * size:(page + 1) * size]], 'total': len(items), 'page': page, 'size': size})
        raise ApiErr(404, 'Recurso não encontrado.')

    def me_view(self, a): return {'id': a['id'], 'name': a['name'], 'email': a['email'], 'role': a['role'], 'company': a.get('company'), 'orgName': S.settings['org']}
    def ai_view(self):
        s = S.settings
        return {'keyMasked': mask(s['aiKey']) if s['aiKey'] else None, 'keySource': 'database' if s['aiKey'] else 'none', 'model': s['model'], 'defaultModel': 'claude-sonnet-4-6', 'maxTokens': s['maxTokens'], 'temperature': s['temperature']}
    def check_client(self, me, cid):
        if me['role'] != 'ADMIN' and me['id'] != cid: raise ApiErr(403, 'Você não tem acesso a este plano.')
        if me['role'] == 'ADMIN' and not (cid in S.accounts and S.accounts[cid]['role'] == 'CLIENT'): raise ApiErr(404, 'Cliente não encontrado.')
    def client(self, cid):
        a = S.accounts.get(cid)
        if not a or a['role'] != 'CLIENT': raise ApiErr(404, 'Cliente não encontrado.')
        return a
    def tab(self, cid, tid):
        t = S.tabs.get(tid)
        if not t or t['clientId'] != cid: raise ApiErr(404, 'Etapa não encontrada.')
        return t


def serve(port=0):
    srv = ThreadingHTTPServer(('127.0.0.1', port), Handler); return srv


if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
    srv = serve(port); print('mock em http://127.0.0.1:%d' % port, flush=True); srv.serve_forever()
