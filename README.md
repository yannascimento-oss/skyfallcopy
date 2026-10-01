# Chat Jr — Seu Plano de Negócios

Interface web (HTML + CSS + JavaScript puro) para consultar o Plano de Negócios da Empresa JR usando a API da Anthropic (Claude).

## Estrutura do projeto

```
chat-jr/
├── index.html          # Estrutura da página e templates de view
├── css/
│   └── styles.css      # Estilos, tokens de tema (claro/escuro) e layout
├── js/
│   └── app.js          # Lógica: dados, roteador, IA, PDF, persistência
├── README.md
├── .gitignore
└── package.json        # Opcional (só para servir com `npm start`)
```

## Como rodar localmente

O projeto é 100% estático — não precisa de build. Duas opções:

### 1. Abrir direto no navegador
Basta abrir `index.html` no navegador. Alguns recursos (como leitura de PDF) precisam de servidor HTTP para funcionar bem.

### 2. Rodar um servidor local (recomendado)
Se você tem Node.js instalado:

```bash
npm start
```

Isso sobe um servidor estático em `http://localhost:8080`.

Ou, sem instalar nada, use a extensão **Live Server** do VS Code — clique com o botão direito no `index.html` e escolha *"Open with Live Server"*.

## Como subir no git

Dentro da pasta `chat-jr/`:

```bash
git init
git add .
git commit -m "Primeira versão do Chat Jr"
git branch -M main
git remote add origin https://github.com/SEU-USUARIO/SEU-REPO.git
git push -u origin main
```

## Perfis de acesso (versão estática)

- **Cliente**: lê o plano, consulta o chat e vê os indicadores de uso. Não vê anexos, processamento, configurações nem a lista de clientes.
- **Administrador (consultoria)**: cadastra clientes, abre o painel de cada um, anexa material, processa com IA e acessa Configurações.
- Não existe tela pública de "Criar conta": o acesso é criado pela consultoria em **Clientes & Planos → Novo cliente**.

Contas de demonstração: `consultoria@empresajr.org` (administrador) e os e-mails dos clientes listados em Clientes & Planos.

> **Atenção: isto não é segurança de verdade.** Esta versão não tem servidor. O perfil vem da conta, mas a senha **não é verificada** e todos os dados ficam no `localStorage` do navegador, então qualquer pessoa com o DevTools consegue ler ou alterar tudo. Autenticação com senha cifrada, autorização por perfil e isolamento entre clientes só existem na versão com backend (Java/Spring Boot). Não coloque dados reais de clientes nesta versão.

## Conteúdo dos planos (administração)

Em **Conteúdo dos planos** a consultoria escolhe um cliente e gerencia cada etapa: título, descrição curta, "o que é", objetivo, principais pontos, perguntas sugeridas, fonte (PDF ou link), processamento com IA e publicação.

- Ao processar uma etapa, a IA devolve o texto **e** os metadados. Só são gravados os campos que o material sustenta; o que a IA deixar vazio não sobrescreve o que a consultoria já editou.
- O HTML vindo da IA passa por uma lista de tags permitidas (`p b ul ol li h4 table tr th td`) antes de ser gravado ou exibido.
- Uma etapa **não publicada** some da visão do cliente e fica fora do contexto do chat.
- Etapas de demonstração mostram só uma definição genérica de "o que é" e "objetivo". Reprocessar com IA troca isso pelo que o material do cliente realmente diz.

## Configuração da IA

- Abra **Configurações → Inteligência artificial** dentro do app.
- Cole sua chave da API Anthropic (formato `sk-ant-...`).
- A chave fica **só no navegador**; nada é enviado para outro servidor.
- Sem chave, o app usa leitura local do PDF como alternativa.

## Persistência

Todos os dados (planos, abas, conversas) ficam salvos no `localStorage` do navegador. Você pode:
- Exportar em JSON pela tela de Configurações.
- Restaurar um JSON exportado antes.
- Apagar tudo e voltar ao estado de demonstração.
