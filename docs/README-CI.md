# Como ativar a integração contínua

O GitHub só aceita arquivos em `.github/workflows/` enviados por um token com o escopo `workflow`.
Por isso o arquivo é criado uma única vez pela interface do GitHub:

1. Abra o repositório no GitHub, troque para a branch `fase-2-java`.
2. **Add file → Create new file**, nome `.github/workflows/ci.yml`.
3. Cole o conteúdo de `docs/github-actions-ci.yml` e confirme (*Commit changes*).

A partir daí cada `push` compila e testa o projeto. O resumo de cada execução fica na branch `ci-logs`.
