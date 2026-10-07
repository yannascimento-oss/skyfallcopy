from playwright.sync_api import sync_playwright
bad=0
with sync_playwright() as p:
    b=p.chromium.launch()
    for i in range(60):
        pg=b.new_page(viewport={'width':1280,'height':800}); pg.goto((__import__('pathlib').Path(__file__).resolve().parent.parent/'index.html').as_uri())
        pg.fill('#login-email','consultoria@empresajr.org'); pg.fill('#login-pass','x'); pg.click('text=Entrar no Chat Jr'); pg.wait_for_selector('#app:not(.hidden)')
        pg.click('[data-view=config]'); pg.click('[data-view=admin]'); pg.click('text=+ Novo cliente')
        pg.fill('#nc-name','Bia Souza'); pg.fill('#nc-email','bia@empresa.com'); pg.fill('#nc-company','Bia Doces'); pg.click('text=Cadastrar cliente')
        try:
            pg.wait_for_function("document.getElementById('admin-tbody').innerText.includes('bia@empresa.com')", timeout=1500)
        except Exception:
            bad+=1
            print('FALHOU na iteração',i, pg.evaluate("""()=>({modalAberto:!document.getElementById('new-client-modal').classList.contains('hidden'),
              vals:['nc-name','nc-email','nc-company'].map(id=>document.getElementById(id).value),
              toast:document.getElementById('toasts').innerText, clientes:clients.length, view:currentView})"""))
        pg.close()
    b.close()
print('falhas:',bad,'de 60')
