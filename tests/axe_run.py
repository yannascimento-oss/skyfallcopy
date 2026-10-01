import sys, json
from playwright.sync_api import sync_playwright
import pathlib
ROOT=(pathlib.Path(__file__).resolve().parent.parent/'index.html').as_uri()
import pathlib
AXE=open(pathlib.Path(__file__).resolve().parent/'node_modules'/'axe-core'/'axe.min.js',encoding='utf-8').read()
def run(pg,label,tags=('wcag2a','wcag2aa','wcag21aa','best-practice')):
    pg.add_script_tag(content=AXE) if not pg.evaluate("typeof axe!=='undefined'") else None
    r=pg.evaluate("(t)=>axe.run(document,{runOnly:{type:'tag',values:t}})",list(tags))
    out=[]
    for v in r['violations']:
        out.append((v['impact'],v['id'],len(v['nodes']),v['nodes'][0]['target'][0][:60]))
    print(f'## {label}: {len(out)} regras violadas')
    for o in sorted(out,key=lambda x:{'critical':0,'serious':1,'moderate':2,'minor':3}.get(x[0],4)): print('   ',o)
    return out
if __name__=='__main__':
    allv=[]
    with sync_playwright() as p:
        b=p.chromium.launch(); pg=b.new_page(viewport={'width':1280,'height':820}); pg.goto(ROOT)
        allv+=run(pg,'landing')
        def login(e): pg.fill('#login-email',e); pg.fill('#login-pass','x'); pg.click('text=Entrar no Chat Jr'); pg.wait_for_selector('#app:not(.hidden)')
        login('ana.silva@nortefit.com.br')
        for v in ['dashboard','plano','chat','indicadores']:
            pg.evaluate(f"go('{v}')"); pg.wait_for_timeout(250); allv+=run(pg,'cliente/'+v)
        pg.click('.logout-btn'); login('consultoria@empresajr.org')
        for v in ['admin','conteudo','config','plano']:
            pg.evaluate(f"go('{v}')"); pg.wait_for_timeout(250); allv+=run(pg,'admin/'+v)
        pg.evaluate("openNewClient()"); pg.wait_for_timeout(150); allv+=run(pg,'modal novo cliente')
        b.close()
    bad=[v for v in allv if v[0] in ('critical','serious')]
    print('\nCRÍTICAS/SÉRIAS:',len(bad)); sys.exit(1 if bad else 0)
