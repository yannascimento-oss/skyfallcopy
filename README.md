# Chat Jr — Seu Plano de Negócios

Plataforma da Empresa JR (Administração UFBA) em que o cliente consulta, em linguagem natural, o Plano de Negócios
entregue pela consultoria. A IA trabalha nos bastidores: lê o material de cada etapa, escreve o conteúdo e responde
somente a partir do plano, sempre indicando a seção usada.

## Estrutura do repositório

| Pasta | O que é |
|---|---|
| `src/`, `pom.xml` | **Aplicação Java** (Spring Boot 3.3, Java 21, PostgreSQL, Flyway) — em construção na Fase 2 |
| `prototipo-estatico/` | Protótipo HTML/JS da Fase 1 (dados no navegador, sem login real). Mantido como demonstração e como referência de interface; ver o README dentro dele |
| `docs/` | Documentação de operação (CI) |

## Estado da Fase 2

| Etapa | Estado |
|---|---|
| Esquema do banco (Flyway) validado em PostgreSQL 16 e H2 | feito |
| Autenticação: BCrypt, sessão, bloqueio após 5 tentativas, convite de 48 h, CSRF, cookies `HttpOnly`/`Secure`/`SameSite=Strict` | escrito, aguardando CI |
| Instalação inicial pela tela e chave da IA cifrada (AES-GCM) | escrito, aguardando CI |
| API do plano, escopo por aba, filtro no servidor | a fazer |
| Upload de PDF (PDFBox), IA, recuperação por relevância (BM25) | a fazer |
| Interface ligada à API e telas de administração | a fazer |
| Docker, README de operação, cobertura mínima de 70 % | a fazer |

## Rodar em desenvolvimento (sem Docker)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Usa H2 em arquivo (`./data`). Abra `http://localhost:8080`.

## Variáveis de ambiente (só para o primeiro boot)

| Variável | Para quê |
|---|---|
| `CHATJR_SECRET` | Texto longo e aleatório (≥ 32 caracteres) que cifra a chave da IA no banco. **Obrigatória em produção** |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL |
| `CHATJR_COOKIE_SECURE` | `true` (padrão). Use `false` só se publicar sem HTTPS |
| `ANTHROPIC_API_KEY` | Valor inicial da chave da IA. Depois do primeiro boot ela é gerida na tela de administração |

Tudo o mais (administrador, nome da consultoria, chave da IA, limites) é configurado pela interface no primeiro acesso.
