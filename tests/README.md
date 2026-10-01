# Testes do Chat Jr

Testes de navegador (Playwright) e auditoria de acessibilidade (axe-core).

```bash
pip install playwright && playwright install chromium
cd tests && npm install axe-core && cd ..
python3 tests/smoke1.py        # perfis cliente x administrador, login, cadastro
python3 tests/smoke2.py        # etapas, conteúdo dos planos, indicadores, sanitização
python3 tests/race_modal.py    # estresse do modal (60 execuções)
python3 tests/axe_run.py   # acessibilidade (WCAG A/AA)
python3 tests/layout_check.py  # modo escuro + rolagem lateral em celular, tablet e notebook
```

A API da Anthropic é simulada nos testes; nenhuma chave é necessária.
