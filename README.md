# Chat Jr — Seu Plano de Negócios

Plataforma da Empresa JR (Administração UFBA). O cliente consulta, em linguagem natural, o Plano de Negócios entregue
pela consultoria. A IA trabalha nos bastidores: lê o PDF de cada etapa, responde **só a partir dele** e indica a etapa
usada. Cada cliente enxerga apenas o próprio plano, e apenas as etapas que a consultoria publicou e liberou.

## Subir o sistema (Docker)

Requisitos: Docker com Compose.

```bash
cp .env.example .env        # preencha DB_PASSWORD e CHATJR_SECRET (instruções dentro do arquivo)
docker compose up -d --build
```

Abra `http://localhost:8080`. No primeiro acesso a tela de **instalação** pede o nome da consultoria, o primeiro
administrador (e-mail e senha) e, se quiser, a chave da IA. Nada mais é configurado em arquivo.

| Variável (`.env`) | Para quê |
|---|---|
| `DB_PASSWORD` | Senha do PostgreSQL. **Obrigatória** |
| `CHATJR_SECRET` | Texto aleatório de 32+ caracteres que cifra a chave da IA no banco. **Obrigatória.** Gere com `openssl rand -base64 48` e guarde: sem ela a chave da IA gravada não pode ser lida (basta cadastrá-la de novo) |
| `CHATJR_COOKIE_SECURE` | `true` (padrão) exige HTTPS, exceto em localhost. Use `false` só se publicar sem HTTPS |
| `ANTHROPIC_API_KEY` | Opcional. Valor inicial da chave; depois é gerida em **Configurações** |

## Dia a dia

- **Convites:** o sistema não envia e-mail. Ao criar um cliente ou administrador, copie o link do convite e envie você
  mesmo. Ele vale 48 horas e só funciona uma vez.
- **Chave da IA, modelo e limites:** tela **Configurações** (inclui "Testar conexão", as últimas 20 chamadas com custo
  estimado e o logo da consultoria); uso e custo dos últimos 30 dias na tela **Sistema**.
- **Pedidos de acesso:** quem não tem acesso usa "Solicitar acesso" na página inicial. O pedido aparece em
  **Clientes & Planos** (o menu mostra quantos estão em aberto); "Criar acesso" já preenche o cadastro.
- **Ver como o cliente:** em **Clientes & Planos → Acesso** ou em **Conteúdo dos planos**, abre o painel exatamente como
  o cliente vê, com faixa amarela de aviso. O chat do cliente não aparece: as conversas são privadas.
- **Dados:** em **Conteúdo dos planos**, baixe o plano em PDF ou JSON e importe um JSON exportado (as etapas casam pelo
  nome; o conteúdo anterior vai para "Versões anteriores"; nada é publicado sozinho).
- **Sem IA:** se a chave faltar ou a API falhar, o cliente recebe os trechos do próprio plano mais próximos da pergunta, e
  o processamento de PDF gera o conteúdo sem reescrita. O sistema continua utilizável.
- **Backup:** guarde o banco e os arquivos.
  ```bash
  docker compose exec db pg_dump -U chatjr chatjr > backup-$(date +%F).sql
  docker run --rm -v chatjr_chatjr-data:/data -v "$PWD":/out alpine tar czf /out/arquivos-$(date +%F).tgz -C /data .
  ```
- **Atualizar:** `git pull && docker compose up -d --build` (as migrações do banco rodam sozinhas).
- **Saúde:** `GET /actuator/health` devolve `{"status":"UP"}`.

## Desenvolvimento

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev      # H2 em arquivo, sem Docker
mvn verify                                              # compila e roda todos os testes (precisa de Docker para o PostgreSQL de teste)
```

Teste de navegador (Playwright), o mesmo que roda no CI contra o jar real:

```bash
pip install playwright && playwright install chromium
python3 e2e/mock_server.py 8099 &                                   # servidor de mentira, só para mexer na interface
BASE_URL=http://127.0.0.1:8099 python3 e2e/run_e2e.py
# contra o sistema real (banco vazio, perfil dev, IA simulada pelo próprio roteiro na porta 9099):
#   java -jar target/*.jar --spring.profiles.active=dev --chatjr.ai.base-url=http://127.0.0.1:9099
#   BASE_URL=http://127.0.0.1:8080 E2E_REAL=1 E2E_AI_STUB_PORT=9099 python3 e2e/run_e2e.py
```

O servidor de mentira imita a API para desenvolver a interface rápido; ele não substitui o backend. Quem vale é o teste
contra o jar real, no CI.

Perfil `demo` (junto de `dev`): cria um administrador e dois clientes de exemplo. Exige `CHATJR_DEMO_PASSWORD` e só roda
em instalação vazia.

## Estrutura

| Pasta | O que é |
|---|---|
| `src/`, `pom.xml` | Aplicação Java (Spring Boot 3.3, Java 21, PostgreSQL, Flyway) |
| `src/main/resources/static/` | Interface web (HTML, CSS e JavaScript em módulos, sem build), servida pela própria aplicação |
| `e2e/` | Teste de navegador da jornada completa e servidor de mentira para desenvolvimento |
| `prototipo-estatico/` | Protótipo HTML da Fase 1 (dados no navegador, sem login real); referência de interface |
| `docs/` | Documentação de operação do CI (o workflow em vigor fica em `.github/workflows/ci.yml`) |

## Segurança, em resumo

Senhas com BCrypt; bloqueio após 5 erros em 15 minutos; sessão de 8 h com cookie `HttpOnly`, `Secure` e
`SameSite=Strict`; proteção contra CSRF; convites de uso único; chave da IA cifrada (AES-256-GCM) e nunca devolvida pela
API; todo HTML vindo da IA ou da consultoria passa por sanitização; trilha de auditoria de quem fez o quê.
A interface só executa scripts do próprio site (`script-src 'self'`, sem scripts em linha), não carrega nada de
servidores externos e não guarda dados do plano no navegador; um teste do build impede que isso regrida.
A cobertura de testes de `domain` e `service` é verificada no build (mínimo de 70%).

## Estado

Fase 2 concluída: backend e interface ligados e testados. O CI roda os testes Java (contra PostgreSQL real) e o mesmo
teste de navegador da jornada completa duas vezes, com IA simulada: contra o jar (perfil `dev`, H2) e contra a pilha do
`docker compose` (imagem do `Dockerfile` + PostgreSQL). O resultado de cada execução fica na branch `ci-logs`.
A correspondência entre os 22 defeitos do briefing e o código está em [`docs/ENTREGA.md`](docs/ENTREGA.md).

Limites conhecidos: o envio de e-mail não existe (convites e redefinição de senha são links que a consultoria copia e
envia); os arquivos estáticos não são guardados em cache pelo navegador (o Spring Security marca tudo como `no-store`),
o que custa cerca de 500 KB por visita. Atrás de proxy reverso (Easypanel, nginx), o sistema usa o IP repassado pelo
proxy só quando a conexão vem de rede interna (`server.forward-headers-strategy: native`).
