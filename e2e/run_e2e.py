#!/usr/bin/env python3
"""Teste de ponta a ponta do Chat Jr pela interface, em navegador de verdade.

Roda igual contra o servidor de mentira (desenvolvimento) e contra o backend real (CI):
    BASE_URL=http://127.0.0.1:8099 python3 e2e/run_e2e.py                  # mock
    BASE_URL=http://127.0.0.1:8080 E2E_REAL=1 E2E_AI_STUB_PORT=9099 ...    # backend real + IA simulada

Nenhuma credencial fica no código: as senhas são geradas a cada execução.
"""
import json, os, re, secrets, sys, threading, time, traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('BASE_URL', 'http://127.0.0.1:8099').rstrip('/')
REAL = os.environ.get('E2E_REAL') == '1'
STUB_PORT = int(os.environ.get('E2E_AI_STUB_PORT') or 0)
AXE = (Path(__file__).parent / 'vendor' / 'axe.min.js').read_text(encoding='utf-8')
ADMIN_EMAIL, CLIENT_EMAIL = 'admin@e2e.chatjr.test', 'rafael@cafeteria.e2e.chatjr.test'
ADMIN_PW, CLIENT_PW, CLIENT_PW2 = ['Aa1-' + secrets.token_hex(8) for _ in range(3)]
FAKE_KEY = 'sk-ant-api03-' + secrets.token_hex(20)

results, console_problems, external_requests = [], [], []
SHOTS = os.environ.get('E2E_SHOTS')  # pasta para guardar capturas de tela (uso manual, para revisão visual)
_shot_n = [0]


def shot(page, name):
    if not SHOTS: return
    _shot_n[0] += 1
    Path(SHOTS).mkdir(parents=True, exist_ok=True)
    page.evaluate("() => { window.scrollTo(0, 0); const c = document.querySelector('#content'); if (c) c.scrollTop = 0; }")
    page.wait_for_timeout(200)
    page.screenshot(path='%s/%02d-%s.png' % (SHOTS, _shot_n[0], re.sub(r'[^a-z0-9]+', '-', name.lower())))


def check(name, ok, detail=''):
    results.append((name, bool(ok), detail))
    print(('  ok   ' if ok else '  FALHA'), name, ('— ' + str(detail) if detail and not ok else ''), flush=True)


def section(title): print('\n== ' + title, flush=True)


def make_pdf(lines):
    def esc(s): return s.encode('cp1252').replace(b'\\', b'\\\\').replace(b'(', b'\\(').replace(b')', b'\\)')
    content = b'BT /F1 12 Tf 50 780 Td\n' + b''.join(b'(' + esc(l) + b') Tj 0 -16 Td\n' for l in lines) + b'ET'
    objs = [b'<< /Type /Catalog /Pages 2 0 R >>', b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
            b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
            b'<< /Length %d >>\nstream\n' % len(content) + content + b'\nendstream',
            b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>']
    out, offsets = b'%PDF-1.4\n', []
    for i, o in enumerate(objs, 1):
        offsets.append(len(out)); out += b'%d 0 obj\n' % i + o + b'\nendobj\n'
    xref = len(out)
    out += b'xref\n0 %d\n0000000000 65535 f \n' % (len(objs) + 1) + b''.join(b'%010d 00000 n \n' % o for o in offsets)
    return out + b'trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n' % (len(objs) + 1, xref)


PLAN_PDF = make_pdf(['Mercado', 'O mercado de academias cresce dez por cento ao ano no Brasil.', 'A concorrência local é formada por seis academias.'])


# ---------------- IA simulada (só no modo real) ----------------
def start_ai_stub():
    class Stub(BaseHTTPRequestHandler):
        def log_message(self, *a): pass
        def do_POST(self):
            body = json.loads(self.rfile.read(int(self.headers.get('Content-Length') or 0)) or b'{}')
            system = body.get('system', '')
            if isinstance(system, list): system = ' '.join(b.get('text', '') for b in system)
            user = body['messages'][0]['content']
            if isinstance(user, list): user = ' '.join(b.get('text', '') for b in user)
            if 'Responda apenas com a palavra OK' in system: text = 'OK'
            elif 'organiza uma etapa' in system:
                text = json.dumps({'title': 'Mercado', 'html': '<p>Conteúdo gerado pela IA de teste.</p>', 'shortDescription': 'Resumo gerado.', 'whatIsIt': 'Análise do mercado.',
                                   'objective': 'Mostrar o tamanho do mercado.', 'keyPoints': ['Cresce 10% ao ano'], 'suggestedQuestions': ['Quanto o mercado cresce?'], 'sections': ['Mercado']}, ensure_ascii=False)
            else:
                m = re.search(r'<trecho etapa="([^"]+)">', user)
                q = re.search(r'<pergunta>(.*?)</pergunta>', user, re.S)
                text = json.dumps({'answerable': True, 'html': '<p>Resposta da IA de teste.</p>', 'source': m.group(1) if m else '',
                                   'inference': bool(q and 'deduz' in q.group(1).lower()), 'queryType': 'INFORMACAO', 'theme': 'Mercado'}, ensure_ascii=False)
            data = json.dumps({'id': 'msg_e2e', 'type': 'message', 'role': 'assistant', 'model': body.get('model'), 'content': [{'type': 'text', 'text': text}],
                               'stop_reason': 'end_turn', 'usage': {'input_tokens': 100, 'output_tokens': 40}}).encode()
            self.send_response(200); self.send_header('Content-Type', 'application/json'); self.send_header('Content-Length', str(len(data))); self.end_headers(); self.wfile.write(data)
    srv = ThreadingHTTPServer(('127.0.0.1', STUB_PORT), Stub)
    threading.Thread(target=srv.serve_forever, daemon=True).start()


# ---------------- apoio de navegador ----------------
def watch(page):
    page.on('pageerror', lambda e: console_problems.append('pageerror: ' + str(e) + ' | ' + ' '.join((getattr(e, 'stack', '') or '').split())[:260]))
    def on_console(msg):
        text = msg.text
        if msg.type == 'error' and 'Failed to load resource' not in text:
            console_problems.append('console: ' + text)
    page.on('console', on_console)
    def on_request(req):
        if not req.url.startswith(BASE) and not req.url.startswith('data:') and not req.url.startswith('blob:'):
            external_requests.append(req.url)
    page.on('request', on_request)


def axe(page, label):
    page.evaluate(AXE)
    res = page.evaluate("axe.run(document, {runOnly:{type:'tag', values:['wcag2a','wcag2aa','wcag21a','wcag21aa']}}).then(r => r.violations.map(v => ({id:v.id, impact:v.impact, n:v.nodes.length, sample:v.nodes[0].html.slice(0,120)})))")
    check('acessibilidade (axe, WCAG A/AA): ' + label, not res, json.dumps(res, ensure_ascii=False)[:400])


def no_overflow(page, label):
    over = page.evaluate("document.documentElement.scrollWidth - window.innerWidth")
    check('sem rolagem lateral: ' + label, over <= 1, 'excesso de %spx' % over)


def settled(page, title):
    """Espera a tela trocar e terminar de carregar. O título muda no mesmo instante em que entra o 'Carregando…'."""
    expect(page.locator('#page-title')).to_have_text(title)
    expect(page.locator('#content .skeleton')).to_have_count(0)
    shot(page, ('m-' if page.viewport_size['width'] < 600 else '') + ('d-' if page.evaluate("document.documentElement.getAttribute('data-theme')") == 'dark' else '') + title)


def go_nav(page, text):
    page.locator('#side-nav').get_by_role('link', name=text, exact=True).click()
    settled(page, text)


def login(page, email, pw):
    page.locator('#login-email').fill(email); page.locator('#login-pass').fill(pw)
    page.get_by_role('button', name='Entrar no Chat Jr').click()


def toast_text(page): return page.locator('#toasts').inner_text()


def run():
    if REAL and STUB_PORT: start_ai_stub()
    with sync_playwright() as p:
        browser = p.chromium.launch()
        admin_ctx = browser.new_context(viewport={'width': 1366, 'height': 820}, locale='pt-BR')
        client_ctx = browser.new_context(viewport={'width': 1366, 'height': 820}, locale='pt-BR')
        admin, client = admin_ctx.new_page(), client_ctx.new_page()
        watch(admin); watch(client)

        # 1 ---------------------------------------------------------------- instalação
        section('Instalação inicial')
        resp = admin.goto(BASE + '/')
        headers = resp.headers
        csp = headers.get('content-security-policy', '')
        script_src = re.search(r"script-src ([^;]*)", csp)
        check('CSP presente, scripts só do próprio site (sem unsafe-inline)', script_src and "'unsafe-inline'" not in script_src.group(1) and "'self'" in script_src.group(1), csp)
        check('X-Frame-Options DENY e nosniff', headers.get('x-frame-options') == 'DENY' and headers.get('x-content-type-options') == 'nosniff')
        expect(admin.get_by_role('heading', name='Instalação inicial')).to_be_visible()
        axe(admin, 'tela de instalação')
        shot(admin, 'instalacao')
        admin.locator('#st-name').fill('Ana Admin'); admin.locator('#st-email').fill(ADMIN_EMAIL)
        admin.locator('#st-pass').fill('curta1'); admin.locator('#st-pass2').fill('curta1')
        admin.get_by_role('button', name='Concluir instalação').click()
        expect(admin.locator('#auth-error')).to_contain_text('pelo menos 10 caracteres')
        admin.locator('#st-pass').fill(ADMIN_PW); admin.locator('#st-pass2').fill(ADMIN_PW + 'x')
        admin.get_by_role('button', name='Concluir instalação').click()
        expect(admin.locator('#auth-error')).to_contain_text('não são iguais')
        admin.locator('#st-pass2').fill(ADMIN_PW)
        admin.get_by_role('button', name='Concluir instalação').click()
        expect(admin.locator('#app')).to_be_visible(timeout=15000)
        expect(admin.locator('#page-title')).to_have_text('Clientes & Planos')
        check('instalação concluída e administrador já entra no painel', True)
        check('menu do administrador não tem telas do cliente', admin.locator('#side-nav a', has_text='Consultar plano').count() == 0)
        axe(admin, 'clientes (vazio)')

        # 2 ---------------------------------------------------------------- cadastro de cliente
        section('Cadastro de cliente e convite')
        admin.get_by_role('button', name='+ Novo cliente').click()
        admin.get_by_role('button', name='Cadastrar cliente').click()
        expect(admin.locator('.modal-error')).to_contain_text('Preencha')
        admin.locator('#nc-name').fill('Rafael Matos'); admin.locator('#nc-email').fill(CLIENT_EMAIL.upper())
        admin.locator('#nc-company').fill('Cafeteria Grão'); admin.locator('#nc-segment').fill('Alimentação')
        axe(admin, 'modal de novo cliente')
        admin.get_by_role('button', name='Cadastrar cliente').click()
        expect(admin.locator('#invite-link')).to_be_visible()
        shot(admin, 'modal-convite')
        invite_link = admin.locator('#invite-link').input_value()
        check('link de convite gerado com o token', '?convite=' in invite_link, invite_link)
        admin.get_by_role('button', name='Copiar link').click()
        axe(admin, 'modal do link de convite')
        admin.get_by_role('button', name='Fechar').click()
        expect(admin.locator('#clients-body')).to_contain_text('Convite pendente')
        check('cliente listado como convite pendente, e-mail em minúsculas', CLIENT_EMAIL in admin.locator('#clients-body').inner_text())

        # 3 ---------------------------------------------------------------- cliente aceita o convite
        section('Cliente define a senha e entra')
        client.goto(invite_link)
        expect(client.get_by_role('heading', name='Crie sua senha')).to_be_visible()
        client.locator('#iv-pass').fill('12345'); client.locator('#iv-pass2').fill('12345')
        client.get_by_role('button', name='Salvar senha').click()
        expect(client.locator('#auth-error')).to_be_visible()
        client.locator('#iv-pass').fill(CLIENT_PW); client.locator('#iv-pass2').fill(CLIENT_PW)
        client.get_by_role('button', name='Salvar senha').click()
        expect(client.get_by_role('heading', name='Acessar meu plano')).to_be_visible()
        check('após definir a senha a URL perde o token do convite', 'convite' not in client.url, client.url)
        axe(client, 'login')
        shot(client, 'login')
        login(client, CLIENT_EMAIL, 'senha-errada-123')
        expect(client.locator('#auth-error')).to_have_text('E-mail ou senha incorretos.')
        login(client, CLIENT_EMAIL, CLIENT_PW)
        expect(client.locator('#app')).to_be_visible(timeout=15000)
        expect(client.locator('#page-title')).to_have_text('Início')
        expect(client.get_by_role('heading', name='Olá, Rafael')).to_be_visible()
        expect(client.locator('.section-head p')).to_contain_text('sendo preparado')
        check('menu do cliente não tem telas da consultoria', client.locator('#side-nav a', has_text='Clientes').count() == 0)
        axe(client, 'início do cliente (plano vazio)')
        go_nav(client, 'Consultar plano')
        client.locator('#chat-input').fill('Quanto o mercado cresce?'); client.locator('#send-btn').click()
        expect(client.locator('.msg-row.ai .msg-bubble').last).to_contain_text('Ainda não há conteúdo liberado', timeout=15000)
        check('chat sem conteúdo publicado explica o motivo', True)

        # 4 ---------------------------------------------------------------- consultoria sobe o PDF, processa e publica
        section('Consultoria monta e publica a etapa')
        admin.get_by_role('link', name='Conteúdo').first.click()
        expect(admin.locator('#page-title')).to_have_text('Conteúdo dos planos')
        admin.locator('.ct-row', has_text='Mercado').first.click()
        expect(admin.locator('#att-panel')).to_contain_text('Nenhum PDF anexado')
        axe(admin, 'conteúdo dos planos')
        admin.set_input_files('#att-file', {'name': 'plano-mercado.pdf', 'mimeType': 'application/pdf', 'buffer': PLAN_PDF})
        expect(admin.locator('#att-panel')).to_contain_text('plano-mercado.pdf', timeout=15000)
        check('PDF anexado e lido', 'caracteres lidos' in admin.locator('#att-panel').inner_text())
        admin.set_input_files('#att-file', {'name': 'falso.pdf', 'mimeType': 'application/pdf', 'buffer': b'isto nao e um pdf'})
        expect(admin.locator('#toasts')).to_contain_text('não parece ser um PDF', timeout=10000)
        admin.get_by_role('button', name='Processar com IA').click()
        expect(admin.locator('#att-panel .pill', has_text='Processada')).to_be_visible(timeout=40000)
        shot(admin, 'conteudo-processado')
        expect(admin.locator('.ct-row', has_text='Mercado').first).to_contain_text('Rascunho', timeout=10000)
        check('a lista de etapas atualiza o status depois do processamento (Vazia → Rascunho)', True)
        check('processamento terminou', True)
        if not STUB_PORT or not REAL:
            check('sem chave da IA: aviso de processamento sem IA', 'sem IA' in admin.locator('#att-panel').inner_text())
        expect(admin.locator('#f-html')).to_have_value(re.compile('dez por cento'))
        admin.get_by_role('button', name='Salvar e publicar').click()
        expect(admin.locator('.ct-detail-head .pill', has_text='Publicada')).to_be_visible(timeout=10000)
        check('etapa publicada', True)
        expect(admin.locator('#ct-detail')).to_contain_text('Versões anteriores')
        check('versão anterior guardada', admin.locator('.version-row').count() >= 1)

        # 5 ---------------------------------------------------------------- cliente lê e pergunta
        section('Cliente lê o plano e pergunta')
        client.reload()
        settled(client, 'Consultar plano')
        check('recarregar a página mantém a tela atual (endereço #/chat)', '#/chat' in client.url, client.url)
        go_nav(client, 'Início')
        expect(client.locator('.kv', has_text='Etapas disponíveis')).to_contain_text('1')
        go_nav(client, 'Plano de negócios')
        expect(client.locator('.plan-content h2')).to_have_text('Mercado')
        expect(client.locator('.plan-content')).to_contain_text('dez por cento')
        check('cliente só vê a etapa publicada', client.locator('.plan-cat-item').count() == 1)
        axe(client, 'plano de negócios')
        client.locator('#plan-search-input').fill('concorrência')
        expect(client.locator('.result-item')).to_have_count(1)
        shot(client, 'plano-busca')
        expect(client.locator('.result-item mark')).to_contain_text('concorrência', ignore_case=True)
        client.locator('#plan-search-input').fill('')
        go_nav(client, 'Consultar plano')
        expect(client.locator('.conv-item')).to_have_count(1)
        client.locator('#chat-input').fill('Quanto o mercado cresce?'); client.locator('#chat-input').press('Enter')
        bubble = client.locator('.msg-row.ai .msg-bubble').last
        expect(bubble).to_contain_text('Fonte no plano', timeout=20000)
        check('resposta cita a etapa de origem', 'Mercado' in bubble.inner_text())
        shot(client, 'chat-resposta')
        if REAL and not STUB_PORT: check('sem chave: aviso de busca direta', client.locator('.msg-note').count() >= 1)
        client.locator('#chat-input').fill('Qual a cor do céu?'); client.locator('#send-btn').click()
        expect(client.locator('.msg-row.ai .msg-bubble').last).to_contain_text('Não encontrei', timeout=15000)
        check('pergunta fora do plano é recusada sem inventar', True)
        axe(client, 'chat com mensagens')
        go_nav(client, 'Indicadores')
        expect(client.locator('.kv', has_text='Consultas realizadas')).to_contain_text('3')
        expect(client.locator('.kv', has_text='Perguntas sem resposta')).to_contain_text('2')
        check('indicadores contam as perguntas reais (3 feitas, 2 sem resposta)', True)
        axe(client, 'indicadores')

        # 6 ---------------------------------------------------------------- escopo
        section('Escopo: etapa bloqueada some para o cliente')
        admin.locator('.ct-row', has_text='Mercado').first.click()
        admin.locator('[data-action="ct-scope"]').click()
        expect(admin.locator('.ct-row', has_text='Mercado').first).to_contain_text('Bloqueada', timeout=10000)
        client.reload(); go_nav(client, 'Plano de negócios')
        expect(client.locator('.card .empty-note')).to_contain_text('ainda não tem etapas publicadas')
        check('etapa bloqueada não aparece no plano do cliente', True)
        admin.locator('[data-action="ct-scope"]').click()
        expect(admin.locator('.ct-row', has_text='Mercado').first).to_contain_text('Publicada', timeout=10000)

        # 7 ---------------------------------------------------------------- configurações e IA
        section('Configurações, IA, sistema e histórico')
        go_nav(admin, 'Configurações')
        axe(admin, 'configurações')
        admin.locator('#org-name').fill('Empresa JR UFBA')
        admin.get_by_role('button', name='Salvar nome').click()
        expect(admin.locator('#active-company')).to_have_text('Empresa JR UFBA', timeout=10000)
        admin.get_by_role('button', name='Testar conexão').click()
        expect(admin.locator('#ai-test-result')).to_contain_text('Nenhuma chave', timeout=10000)
        check('testar conexão sem chave explica o que falta', True)
        admin.locator('#ai-key').fill('chave-invalida')
        admin.get_by_role('button', name='Salvar', exact=True).click()
        expect(admin.locator('#ai-error')).to_contain_text('sk-ant-')
        admin.locator('#ai-key').fill(FAKE_KEY)
        admin.get_by_role('button', name='Salvar', exact=True).click()
        expect(admin.locator('#toasts')).to_contain_text('Configuração da IA salva', timeout=10000)
        expect(admin.locator('#ai-badge')).to_have_text('IA conectada')
        page_text = admin.locator('#content').inner_text()
        check('a chave nunca volta inteira para a tela (só mascarada)', FAKE_KEY not in page_text and FAKE_KEY[:7] in page_text and FAKE_KEY[-4:] in page_text)
        admin.get_by_role('button', name='Testar conexão').click()
        expect(admin.locator('#ai-test-result')).to_contain_text('funcionando', timeout=15000)
        check('testar conexão com chave funciona', True)
        admin.locator('#lim-q').fill('0'); admin.get_by_role('button', name='Salvar limites').click()
        expect(admin.locator('#limits-error')).to_contain_text('pelo menos 1')
        admin.locator('#lim-q').fill('50'); admin.get_by_role('button', name='Salvar limites').click()
        expect(admin.locator('#toasts')).to_contain_text('Limites salvos', timeout=10000)

        # com a chave: nova pergunta vem da IA, sem aviso de busca direta
        client.reload(); go_nav(client, 'Plano de negócios')
        # (a etapa foi liberada de novo acima)
        go_nav(client, 'Consultar plano'); client.locator('.new-conv-btn').click()
        client.locator('#chat-input').fill('Quanto o mercado cresce ao ano?'); client.locator('#send-btn').click()
        ai_bubble = client.locator('.msg-row.ai .msg-bubble').last
        expect(ai_bubble).to_contain_text('Fonte no plano', timeout=20000)
        check('com chave, a resposta vem da IA (sem aviso de busca direta)', client.locator('.msg-row.ai .msg-note').count() == 0)
        if REAL and STUB_PORT:
            expect(ai_bubble).to_contain_text('Resposta da IA de teste')
            client.locator('#chat-input').fill('O mercado cresce. Dá para deduzir algo sobre o ano?'); client.locator('#send-btn').click()
            expect(client.locator('.msg-infer-tag').last).to_have_text('Inferência', timeout=20000)
            check('rótulo "Inferência" aparece quando a IA deduz', True)

        go_nav(admin, 'Sistema')
        expect(admin.locator('#content')).to_contain_text('Em números')
        check('tela Sistema mostra 1 cliente', admin.locator('.stat-card', has_text='Clientes').inner_text().split('\n')[-1].strip() in ('1', 'Clientes\n1') or '1' in admin.locator('.stat-card', has_text='Clientes').inner_text())
        admin.get_by_role('button', name='Testar e-mail').click()
        expect(admin.locator('#toasts')).to_contain_text('não está configurado', timeout=10000)
        axe(admin, 'sistema')
        go_nav(admin, 'Histórico')
        expect(admin.locator('.audit-table')).to_contain_text('Cliente cadastrado', timeout=10000)
        admin.locator('#au-action').select_option('CLIENT_CREATED')
        expect(admin.locator('.audit-table tbody tr')).to_have_count(1, timeout=10000)
        check('histórico filtra por ação', True)
        axe(admin, 'histórico')

        # 8 ---------------------------------------------------------------- senha, suspensão
        section('Troca de senha e suspensão')
        go_nav(client, 'Minha conta')
        axe(client, 'minha conta')
        client.locator('#pw-current').fill('senha-atual-errada1'); client.locator('#pw-new').fill(CLIENT_PW2); client.locator('#pw-confirm').fill(CLIENT_PW2)
        client.get_by_role('button', name='Salvar nova senha').click()
        expect(client.locator('#pw-error')).to_contain_text('senha atual está incorreta')
        client.locator('#pw-current').fill(CLIENT_PW)
        client.get_by_role('button', name='Salvar nova senha').click()
        expect(client.locator('#toasts')).to_contain_text('Senha alterada', timeout=10000)
        client.locator('.logout-btn').click()
        expect(client.get_by_role('heading', name='Acessar meu plano')).to_be_visible()
        login(client, CLIENT_EMAIL, CLIENT_PW)
        expect(client.locator('#auth-error')).to_have_text('E-mail ou senha incorretos.')
        login(client, CLIENT_EMAIL, CLIENT_PW2)
        expect(client.locator('#app')).to_be_visible(timeout=15000)
        check('senha trocada: a antiga deixa de valer, a nova entra', True)

        go_nav(admin, 'Clientes & Planos')
        admin.get_by_role('button', name='Gerenciar acesso de Cafeteria Grão').click()
        shot(admin, 'modal-acesso')
        admin.get_by_role('button', name='Suspender acesso').click()
        admin.get_by_role('button', name='Suspender', exact=True).click()
        expect(admin.locator('#clients-body')).to_contain_text('Acesso suspenso', timeout=10000)
        client.locator('#side-nav').get_by_role('link', name='Plano de negócios').click()
        expect(client.get_by_role('heading', name='Acessar meu plano')).to_be_visible(timeout=15000)
        check('suspensão encerra a sessão aberta do cliente', True)
        login(client, CLIENT_EMAIL, CLIENT_PW2)
        expect(client.locator('#auth-error')).to_contain_text('suspenso')
        admin.get_by_role('button', name='Gerenciar acesso de Cafeteria Grão').click()
        admin.get_by_role('button', name='Reativar acesso').click()
        admin.get_by_role('button', name='Reativar', exact=True).click()
        expect(admin.locator('#clients-body')).to_contain_text('Ativo', timeout=10000)
        login(client, CLIENT_EMAIL, CLIENT_PW2)
        expect(client.locator('#app')).to_be_visible(timeout=15000)
        check('reativado, o cliente volta a entrar', True)

        # 9 ---------------------------------------------------------------- segurança dos cookies
        section('Cookies e requisições')
        cookies = {c['name']: c for c in client_ctx.cookies()}
        sess = cookies.get('JSESSIONID')
        check('cookie de sessão: HttpOnly e SameSite=Strict', sess and sess['httpOnly'] and sess['sameSite'] == 'Strict', sess)
        xsrf = cookies.get('XSRF-TOKEN')
        check('cookie XSRF-TOKEN legível pelo script e SameSite=Strict', xsrf and not xsrf['httpOnly'] and xsrf['sameSite'] == 'Strict', xsrf)
        check('nenhuma requisição a servidores externos (fontes e scripts locais)', not external_requests, external_requests[:3])
        check('sem erros de console, exceções nem violações de CSP', not console_problems, console_problems[:3])

        # 10 --------------------------------------------------------------- celular e modo escuro
        section('Celular e modo escuro')
        for ctx_page, label, paths in ((client, 'cliente', ['Início', 'Consultar plano', 'Plano de negócios', 'Indicadores', 'Minha conta']),
                                       (admin, 'administrador', ['Clientes & Planos', 'Conteúdo dos planos', 'Indicadores', 'Histórico', 'Configurações', 'Sistema'])):
            for dark in (False, True):
                ctx_page.set_viewport_size({'width': 390, 'height': 844})
                ctx_page.evaluate("t => document.documentElement.setAttribute('data-theme', t)", 'dark' if dark else 'light')
                ctx_page.locator('.mobile-menu-btn').click()
                for name in paths:
                    link = ctx_page.locator('#side-nav').get_by_role('link', name=name, exact=True)
                    if not ctx_page.locator('#sidebar.open').count(): ctx_page.locator('.mobile-menu-btn').click()
                    link.click()
                    settled(ctx_page, name)
                    no_overflow(ctx_page, '%s · %s · %s' % (label, name, 'escuro' if dark else 'claro'))
                    if label == 'cliente' and name == 'Consultar plano' and not dark:
                        expect(ctx_page.locator('#conv-pick')).to_be_visible()
                        check('celular: seletor de conversas e botão "Nova conversa" visíveis', ctx_page.locator('#conv-pick option').count() >= 2 and ctx_page.locator('.mobile-conv').get_by_role('button', name='Nova conversa').is_visible())
            ctx_page.evaluate("document.documentElement.setAttribute('data-theme','light')")
            ctx_page.set_viewport_size({'width': 1366, 'height': 820})
        axe_dark = client
        axe_dark.evaluate("document.documentElement.setAttribute('data-theme','dark')")
        axe(axe_dark, 'modo escuro (cliente)')
        go_nav(axe_dark, 'Consultar plano'); axe(axe_dark, 'modo escuro (chat)'); shot(axe_dark, 'escuro-chat')
        go_nav(axe_dark, 'Plano de negócios'); axe(axe_dark, 'modo escuro (plano)')
        admin.evaluate("document.documentElement.setAttribute('data-theme','dark')")
        go_nav(admin, 'Conteúdo dos planos'); admin.locator('.ct-row', has_text='Mercado').first.click(); axe(admin, 'modo escuro (conteúdo)'); shot(admin, 'escuro-conteudo')
        go_nav(admin, 'Configurações'); axe(admin, 'modo escuro (configurações)')
        check('sem erros de console depois da navegação móvel', not console_problems, console_problems[:3])
        browser.close()


try:
    run()
except Exception as exc:  # falha inesperada do próprio roteiro
    traceback.print_exc()
    check('roteiro concluído sem exceção', False, str(exc)[:300])

failed = [r for r in results if not r[1]]
print('\n%d verificações, %d com falha' % (len(results), len(failed)))
for name, _, detail in failed: print('  FALHA:', name, '—', detail)
sys.exit(1 if failed else 0)
