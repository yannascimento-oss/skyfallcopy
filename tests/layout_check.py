import sys
import pathlib
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent))
from playwright.sync_api import sync_playwright
from axe_run import run, ROOT
bad=[]
with sync_playwright() as p:
    b=p.chromium.launch()
    # ---- modo escuro + axe
    pg=b.new_page(viewport={'width':1280,'height':820}); pg.goto(ROOT)
    pg.fill('#login-email','ana.silva@nortefit.com.br'); pg.fill('#login-pass','x'); pg.click('text=Entrar no Chat Jr'); pg.wait_for_selector('#app:not(.hidden)')
    pg.evaluate("applyTheme('dark')")
    for v in ['dashboard','plano','chat','indicadores']:
        pg.evaluate(f"go('{v}')"); pg.wait_for_timeout(200)
        r=[x for x in run(pg,'ESCURO cliente/'+v,('wcag2a','wcag2aa')) if x[0] in('critical','serious')]; bad+=r
    pg.click('.logout-btn'); pg.fill('#login-email','consultoria@empresajr.org'); pg.fill('#login-pass','x'); pg.click('text=Entrar no Chat Jr'); pg.wait_for_selector('#app:not(.hidden)')
    pg.evaluate("applyTheme('dark')")
    for v in ['admin','conteudo','config','plano','indicadores']:
        pg.evaluate(f"go('{v}')"); pg.wait_for_timeout(200)
        r=[x for x in run(pg,'ESCURO admin/'+v,('wcag2a','wcag2aa')) if x[0] in('critical','serious')]; bad+=r
    pg.evaluate("openNewClient()"); pg.wait_for_timeout(150)
    bad+=[x for x in run(pg,'ESCURO modal',('wcag2a','wcag2aa')) if x[0] in('critical','serious')]
    pg.close()
    # ---- overflow horizontal em vários tamanhos
    for (w,hh,name) in [(390,844,'celular'),(768,1024,'tablet'),(1024,768,'notebook'),(1440,900,'desktop')]:
        for role,email,views in [('cliente','ana.silva@nortefit.com.br',['dashboard','plano','chat','indicadores']),
                                 ('admin','consultoria@empresajr.org',['admin','conteudo','config','plano'])]:
            pg=b.new_page(viewport={'width':w,'height':hh}); pg.goto(ROOT)
            o=pg.evaluate("document.documentElement.scrollWidth-window.innerWidth")
            if o>1: bad.append(('overflow',f'{name} landing',o))
            pg.fill('#login-email',email); pg.fill('#login-pass','x'); pg.click('text=Entrar no Chat Jr'); pg.wait_for_selector('#app:not(.hidden)')
            for v in views:
                pg.evaluate(f"go('{v}')"); pg.wait_for_timeout(150)
                o=pg.evaluate("document.documentElement.scrollWidth-window.innerWidth")
                culprit=''
                if o>1:
                    culprit=pg.evaluate("""()=>{const w=window.innerWidth;let r=[];document.querySelectorAll('#content *').forEach(e=>{const b=e.getBoundingClientRect();if(b.right>w+1&&b.width>0)r.push(e.tagName+'.'+(e.className||'').toString().split(' ')[0]+' '+Math.round(b.right))});return r.slice(0,3).join(', ')}""")
                    bad.append(('overflow',f'{name}/{role}/{v}',o,culprit))
                print(f'{name:9}{role:8}{v:12} overflow={max(o,0)}px {culprit}')
            pg.close()
    b.close()
print('\nPROBLEMAS:',bad); sys.exit(1 if bad else 0)
