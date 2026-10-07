# Entrega da Fase 2 — correspondência com o briefing

Briefing de origem: `PROMPT-CLAUDE-CODE.md` (22 defeitos, telas obrigatórias e critérios de aceite).
Caminhos abaixo relativos a `src/main/java/org/empresajr/chatjr/` (Java) e `src/main/resources/static/` (interface).

## Os 22 defeitos

| # | Defeito | Onde foi corrigido | Prova |
|---|---|---|---|
| 1 | Chave da IA no navegador | `service/SecretCipher`, `service/SettingsService`, `service/SettingsAdminService`, `web/AdminSettingsController`; tela `js/views/admin-settings.js` | `AdminSettingsTest`, `AuthFlowTest` (a chave não aparece em nenhuma resposta), e2e |
| 2 | Login sem senha | `service/AuthService`, `domain/ClientAccount` (bloqueio 5/15 min), `domain/PasswordPolicy`, `service/AccountService` (convite de 48 h) | `AuthFlowTest`, `AccountSelfServiceTest`, e2e |
| 3 | Cookie sem Secure, sem CSRF | `config/SecurityConfig`, `config/SpaCsrfTokenRequestHandler`, `config/CsrfCookieFilter`; `js/api.js` | `SessionCookieTest`, e2e (cookies) |
| 4 | Estado no localStorage | Tudo no banco; `localStorage` só guarda o tema (`js/ui.js`) | `StaticFrontendGuardTest`, e2e (consultoria e cliente veem o mesmo plano) |
| 5 | JSON regravado inteiro | `db/migration/V1__schema.sql`, `V2__access_request.sql`, entidades em `domain/` | Testcontainers PostgreSQL em todos os testes de integração |
| 6 | Ids `int` de timestamp | Ids `BIGINT` gerados pelo banco | Esquema |
| 7 | pdf.js no navegador | `service/PdfTextExtractor` (PDFBox), `service/AttachmentService` (25 MB, 200 páginas) | `ProcessingTest` |
| 8 | Sem limite de corpo | `config/RequestSizeFilter` (2 MB; 8 MB na importação), multipart de 25 MB, `web/GlobalExceptionHandler` (413) | `AccessRequestsAndDataTest`, `ProcessingTest` |
| 9 | Contexto truncado, `max_tokens` fixo | `domain/KnowledgeIndex` (BM25), `domain/PromptBuilder`, `max_tokens` na tela Configurações | `KnowledgeIndexTest`, `ChatTest` |
| 10 | Sem testes | `src/test/java/...` (JUnit 5, Testcontainers), JaCoCo ≥ 70 % em `domain` e `service`, `e2e/run_e2e.py` | CI |
| 11 | Escopo filtrado no JavaScript | `service/PlanService.visibleTabs`, `service/ChatService` | `PlanAccessTest`, `ChatTest` (aba bloqueada não entra no contexto), e2e |
| 12 | "Criar conta" que não cria nada | Escolha: **"Solicitar acesso"**, que grava o pedido e avisa a consultoria (contador no menu). `domain/AccessRequest`, `service/AccessRequestService`, `web/AccessRequestController`; `js/views/auth.js`, `js/views/admin-clients.js` | `AccessRequestsAndDataTest`, e2e |
| 13 | Sem auditoria | `domain/AuditLog`, `service/AuditService`; tela Histórico `js/views/admin-audit.js` (filtro por cliente e ação) | `AdminSettingsTest`, e2e |
| 14 | Versão decorativa | `domain/PlanVersion`, restauração em `service/PlanService`; tela Conteúdo dos planos | `PlanAccessTest` |
| 15 | `alert()` | Avisos e diálogos próprios em `js/ui.js`, com "Tentar de novo" em falhas de rede e carregamento | `StaticFrontendGuardTest` (sem `alert`/`onclick`), e2e |
| 16 | Indicadores fixos | `domain/IndicatorCalculator`, `service/IndicatorService`; `js/views/indicators.js` (consultas, taxa com fonte, etapas mais consultadas, perguntas frequentes, etapas publicadas) | `KnowledgeIndexTest`, `ChatTest`, e2e |
| 17 | Sem visibilidade | `/actuator/health`, `service/SystemInfoService`; tela Sistema | `AdminSettingsTest` |
| 18 | Sem limite de uso | `service/LimitService` (60 perguntas/h, 20 processamentos/dia), configurável na tela | `ChatTest`, `ProcessingTest`, `AdminSettingsTest` |
| 19 | Sem implantação | `Dockerfile`, `docker-compose.yml`, `.env.example`, `README.md` | **Não executado** (sem Docker no ambiente de desenvolvimento) |
| 20 | Arquivo grande ou lento | Barra de progresso real no envio (`js/api.js`, `uploadWithProgress`); processamento assíncrono com estado no servidor, dá para sair e voltar | `ProcessingTest`, e2e |
| 21 | Sem exportação | `service/ExportService`, `service/PdfExporter`; botões no Plano (cliente) e em Conteúdo dos planos (consultoria), mais importação de JSON | `ExportTest`, `AccessRequestsAndDataTest` |
| 22 | Acessibilidade | Navegação por teclado, `aria-label`, foco visível, contraste AA, diálogos com foco preso | axe-core no e2e (todas as telas, claro e escuro) |

## Telas obrigatórias ("Tudo vira tela")

| Tela pedida | Onde está |
|---|---|
| Integração de IA | Configurações → Inteligência artificial (chave mascarada, modelo, `max_tokens`, temperatura, testar conexão, últimas 20 chamadas com custo, status e tempo) |
| Usuários & Acessos | Clientes & Planos (criar, convite, redefinir senha, suspender, promover a administrador, último acesso) e Configurações → Administradores |
| Escopo por aba | Conteúdo dos planos → "Liberada para este cliente" |
| Sistema | Sistema (banco, IA, versão, disco, últimos erros, teste de e-mail) |
| Limites | Configurações → Limites de uso |
| Dados | Conteúdo dos planos (baixar PDF e JSON, importar JSON) e Clientes & Planos → Acesso → Excluir (confirmação digitando o e-mail, que é único; o nome pode repetir) |
| Histórico | Histórico |
| Assistente de instalação | Primeiro acesso: consultoria, administrador, logo e chave da IA |
| Painel do cliente pela consultoria | "Ver como o cliente", com faixa amarela (o chat fica de fora: as conversas são privadas) |

## Critérios de aceite

| Critério | Estado |
|---|---|
| `docker compose up` sobe tudo | **Não verificado**: não houve Docker disponível. É o primeiro passo antes da produção |
| Assistente de instalação na primeira subida | Verificado (e2e contra o jar real) |
| Consultoria e cliente veem o mesmo plano | Verificado (e2e, dois navegadores) |
| Toda aba, inclusive nova, tem PDF, link e processar | Verificado |
| PDF de 10 MB extraído no servidor | Limite de 25 MB testado; um PDF real de 10 MB não foi testado |
| Sem chave, processamento extrativo e chat com fonte | Verificado (`ProcessingTest`, `ChatTest`, e2e) |
| Aba bloqueada fora da API e do chat | Verificado por teste automatizado |
| 403 no plano de outro cliente | Verificado (`PlanAccessTest`, `ExportTest`) |
| Chave nunca nas respostas | Verificado por teste |
| `mvn verify` com cobertura ≥ 70 % | Verificado no CI |
| axe-core sem violação crítica | Verificado: zero violações WCAG A/AA em todas as telas |
| Nenhum `alert()` | Verificado (`StaticFrontendGuardTest`) |

## Divergências conscientes

- Pastas da interface: `css/` e `js/` em vez de `assets/`.
- Dados de demonstração pelo perfil `demo` em vez de `V2__seed_demo.sql`: o briefing proíbe credenciais em migração.
  A `V2` do projeto é a tabela de pedidos de acesso.
- Lista de tags permitidas no HTML com `strong`, `thead`, `tbody` e `br` além das pedidas.
- Envio de e-mail não existe: convites e redefinições são links que a consultoria copia.
