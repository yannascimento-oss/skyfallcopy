import sys, json
from playwright.sync_api import sync_playwright
import pathlib
ROOT=(pathlib.Path(__file__).resolve().parent.parent/'index.html').as_uri()
res=[]; errs=[]; reqs=[]
def ok(n,c): res.append((n,bool(c))); print(('PASS ' if c else 'FAIL ')+n)

AI_PROC={"titulo":"Público-Alvo","descricaoCurta":"Quem a empresa quer atender.","oQueE":"Descreve os clientes-alvo.","objetivo":"Orientar a comunicação.",
 "html":"<p>Adultos de 25 a 45 anos.</p><script>window.__pwned=1</script><img src=x onerror='window.__pwned=1'><b onclick='window.__pwned=1'>R$ 240 de ticket</b><ul><li>Moram a 3 km</li></ul>",
 "pontosPrincipais":["Faixa etária 25–45","Raio de 3 km"],"perguntasSugeridas":["Qual a faixa etária?"],"sections":["Perfil"]}
AI_CHAT={"html":"<p>O ponto de equilíbrio é de R$ 38.400/mês.</p><iframe src='x'></iframe>","fonte":"Plano Financeiro","inferencia":False,"tipo":"Informação"}
AI_CHAT_NONE={"html":"<p>Essa informação não está no plano.</p>","fonte":"","inferencia":False,"tipo":"Decisão"}

def handler_factory(state):
    def h(route):
        body=json.loads(route.request.post_data or '{}'); reqs.append(body)
        sysmsg=body.get('system','') or ''
        if 'Responda apenas: ok' in json.dumps(body.get('messages','')): txt='ok'
        elif 'analista de negócios' in sysmsg: txt=json.dumps(AI_PROC)
        else: txt=json.dumps(state['chat'])
        route.fulfill(status=200, headers={'access-control-allow-origin':'*'}, content_type='application/json',
                      body=json.dumps({'content':[{'type':'text','text':txt}]}))
    return h

with sync_playwright() as p:
    b=p.chromium.launch()
    def fresh(mock=True, state=None):
        ctx=b.new_context(viewport={'width':1280,'height':800}); pg=ctx.new_page()
        pg.on('pageerror',lambda e:errs.append(str(e))); pg.on('dialog',lambda d:(errs.append('DIALOG:'+d.message),d.dismiss()))
        if mock:
            def pre(route):
                if route.request.method=='OPTIONS': route.fulfill(status=204,headers={'access-control-allow-origin':'*','access-control-allow-headers':'*','access-control-allow-methods':'*'})
                else: handler_factory(state)(route)
            pg.route('https://api.anthropic.com/**', pre)
        pg.goto(ROOT); return pg
    def login(pg,e):
        pg.fill('#login-email',e); pg.fill('#login-pass','x'); pg.click('text=Entrar no Chat Jr'); pg.wait_for_selector('#app:not(.hidden)')

    # ---------- cliente: etapa como documento ----------
    pg=fresh(False); login(pg,'ana.silva@nortefit.com.br'); pg.click('[data-view=plano]')
    pg.click('.plan-cat-item:has-text("Público-Alvo")')
    t=pg.text_content('#plan-content-wrap')
    ok('etapa: "O que é esta etapa?"', 'O que é esta etapa?' in t)
    ok('etapa: Objetivo', 'Objetivo' in t)
    ok('etapa: Conteúdo', 'Conteúdo' in t)
    ok('etapa: sem "Principais pontos" repetido quando não há pontos', 'Principais pontos' not in t and 'Já frequentaram' in t)
    ok('etapa: Atualizado em', 'Atualizado em' in t)
    ok('etapa: sem termos técnicos p/ cliente', not any(x in t for x in ['Extraído pela IA','Rascunho','Processar','chunks']))
    # chat sugestões vêm das etapas
    pg.click('[data-view=chat]'); sug=pg.inner_text('.chat-empty')
    ok('chat: sugestões das etapas (Público-Alvo/Financeiro)', 'Quem é o nosso cliente ideal?' in sug or 'Qual é o ponto de equilíbrio?' in sug)
    # indicadores do cliente
    pg.click('[data-view=indicadores]'); ind=pg.inner_text('#content')
    ok('indicadores: temas mais consultados com %', 'Temas mais consultados' in ind and '%' in ind)
    ok('indicadores: perguntas frequentes', 'Perguntas mais frequentes' in ind and 'ponto de equilíbrio' in ind.lower())
    ok('indicadores: perguntas sem resposta (n e %)', 'Perguntas sem resposta no plano' in ind and '4 (9%)' in ind)
    ok('indicadores: tipos de consulta', all(k in ind for k in ['Informação','Dúvida','Interpretação','Decisão']))
    ok('indicadores: oportunidades de melhoria lista perguntas+tema', 'Oportunidades de melhoria' in ind and 'política de cancelamento' in ind and 'Produto/Serviço' in ind)
    ok('indicadores: sem métricas técnicas', not any(x in ind for x in ['Abas processadas','chunk','IA conectada']))
    ok('indicadores: sem rosca', pg.locator('#content svg.donut').count()==0)
    pg.close()

    # ---------- chat registra tipo e lacunas (IA simulada) ----------
    st={'chat':AI_CHAT_NONE}
    pg=fresh(True,st); login(pg,'ana.silva@nortefit.com.br'); pg.click('[data-view=chat]')
    pg.fill('#chat-input','Devo abrir a segunda unidade já no mês 6?'); pg.press('#chat-input','Enter'); pg.wait_for_timeout(1200)
    d=pg.evaluate("({u:C().unanswered[0], t:C().queryTypes, n:C().consultas})")
    ok('lacuna registrada com a pergunta', d['u'] and 'segunda unidade' in d['u']['q'])
    ok('lacuna com tema sugerido', d['u'] and d['u']['theme'] and d['u']['theme']!='')
    ok('tipo vindo da IA contabilizado (Decisão 5→6)', d['t']['Decisão']==6)
    st['chat']=AI_CHAT
    pg.fill('#chat-input','Qual o ponto de equilíbrio?'); pg.press('#chat-input','Enter'); pg.wait_for_timeout(1200)
    ok('resposta da IA sanitizada (sem iframe)', pg.locator('#chat-thread iframe').count()==0)
    ok('resposta mostra Fonte', 'Plano Financeiro' in pg.inner_text('#chat-thread'))
    pg.close()

    # ---------- admin: Conteúdo dos planos ----------
    st={'chat':AI_CHAT}
    pg=fresh(True,st); login(pg,'consultoria@empresajr.org')
    cols=pg.inner_text('.admin-table thead')
    ok('clientes: colunas pedidas', all(x in cols for x in ['Cliente','Empresa','E-mail','Plano','Status','Último acesso']))
    ok('clientes: ações Abrir painel/Gerenciar acesso/Gerenciar conteúdo', all(pg.locator(f'text={x}').count()>0 for x in ['Abrir painel','Gerenciar acesso','Gerenciar conteúdo']))
    pg.click('tr:has-text("Rafael") >> text=Gerenciar conteúdo')
    ok('conteúdo: abre no cliente certo', 'Cafeteria' in pg.inner_text('#ct-summary'))
    ok('conteúdo: lista 12 seções com status', pg.locator('.ct-row').count()==12 and 'Sem material' in pg.inner_text('#ct-list'))
    pg.click('.ct-row:has-text("Público-Alvo")')
    pg.fill('#slide-link-input','https://docs.google.com/x'); pg.click('text=Salvar link')
    ok('conteúdo: link vira "Aguardando processamento"', 'Aguardando processamento' in pg.inner_text('#ct-list'))
    pg.click('text=Processar com IA'); pg.wait_for_timeout(1500)
    ok('IA: pediu max_tokens maior que 1000', any(r.get('max_tokens',0)>=3000 for r in reqs))
    ok('IA: HTML malicioso neutralizado', pg.evaluate("window.__pwned")!=1 and pg.locator('.ct-preview script, .ct-preview img, .ct-preview [onclick]').count()==0)
    ok('IA: tags permitidas preservadas', pg.locator('.ct-preview ul li').count()==1 and 'R$ 240' in pg.inner_text('.ct-preview'))
    ok('conteúdo: status Publicado após processar', 'Publicado' in pg.inner_text('.ct-detail-head'))
    ok('conteúdo: metadados da IA nos campos', pg.input_value('#ct-obj')=='Orientar a comunicação.' and 'Raio de 3 km' in pg.input_value('#ct-points'))
    ok('conteúdo: fonte registrada', pg.evaluate("currentTab().meta.source")=='https://docs.google.com/x')
    # editar metadados
    pg.fill('#ct-obj','Objetivo editado'); pg.click('text=Salvar metadados')
    ok('conteúdo: edição de metadados persiste', pg.evaluate("currentTab().meta.objective")=='Objetivo editado')
        # despublicar -> cliente não vê
    pg.click('text=Despublicar'); ok('conteúdo: despublicado', 'Não publicado' in pg.inner_text('.ct-detail-head'))
    pg.click('.logout-btn')
    login(pg,'rafael@graoecia.com.br'); pg.click('[data-view=plano]')
    ok('cliente: etapa despublicada some da lista', pg.locator('.plan-cat-item:has-text("Público-Alvo")').count()==0)
    ok('cliente: etapa despublicada fora do contexto do chat', 'Público-Alvo' not in pg.evaluate("knowledgeBase()"))
    pg.click('.logout-btn'); login(pg,'consultoria@empresajr.org')
    pg.evaluate("openContentFor(2)"); pg.click('.ct-row:has-text("Público-Alvo")'); pg.click('text=Publicar para o cliente')
    pg.click('.logout-btn'); login(pg,'rafael@graoecia.com.br'); pg.click('[data-view=plano]'); pg.click('.plan-cat-item:has-text("Público-Alvo")')
    t=pg.text_content('#plan-content-wrap')
    ok('cliente: publicado mostra O que é / Objetivo / Principais pontos', all(x in t for x in ['O que é esta etapa?','Objetivo editado','Principais pontos','Raio de 3 km']))
    ok('cliente: conteúdo da IA aparece, sem o script', 'R$ 240' in t and pg.evaluate("window.__pwned")!=1)
    # cliente não alcança o gerenciador
    pg.evaluate("go('conteudo')"); ok('cliente: go(conteudo) bloqueado', 'Visão geral' in pg.inner_text('#content'))
    pg.evaluate("openContentFor(2)"); ok('cliente: openContentFor ignorado', 'Visão geral' in pg.inner_text('#content'))
    # persistência: meta sobrevive a reload
    pg.reload(); pg.wait_for_selector('#app:not(.hidden)'); pg.click('[data-view=plano]'); pg.click('.plan-cat-item:has-text("Público-Alvo")')
    ok('reload: metadados mantidos', 'Objetivo editado' in pg.inner_text('#plan-content-wrap'))
    pg.close(); b.close()

fails=[n for n,c in res if not c]
print('\nTOTAL',len(res),'FALHAS',len(fails),fails); print('ERROS:',errs)
sys.exit(1 if fails or errs else 0)
