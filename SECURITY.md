# Política de segurança

FraudGuard é um projeto acadêmico. Ele não foi submetido a auditoria externa nem a teste de intrusão, e **não deve ser colocado em produção como está**.

## Versões suportadas

Só a branch `main` recebe correções.

## Como reportar uma vulnerabilidade

Não abra uma issue pública. Use o relatório privado de vulnerabilidades do GitHub (aba **Security** → **Report a vulnerability**) neste repositório.

Resposta esperada: confirmação de recebimento em até 7 dias e plano de correção ou justificativa em até 30 dias, por melhor esforço, já que o projeto é mantido por uma pessoa.

## O que foi endurecido

- Sem credenciais padrão: senhas dos usuários da aplicação, do banco e do Grafana vêm de variáveis de ambiente obrigatórias e a aplicação não inicia sem elas (`.env.example` só tem placeholders).
- Endpoints de métricas e health em porta de gestão separada, não publicada pelo `docker-compose`; demais portas publicadas apenas em `127.0.0.1`.
- Autorização por perfil: `SERVICE` avalia, `ANALISTA` resolve casos, `ADMIN` altera regras. Todo o resto é negado.
- Validação de tamanho e formato nas entradas; parâmetros de regras validados antes de gravar.
- Dados pessoais minimizados: a decisão guarda apenas id da transação, score, motivos e versão; identificadores vão hasheados ao Redis e mascarados nos logs.
- Imagem Docker com base fixada por digest, usuário não-root, `no-new-privileges` e sem capabilities no Compose; GitHub Actions com permissões mínimas e ações fixadas por SHA.

## Dependências e imagem

A imagem final (Alpine, base fixada por digest) foi varrida com o trivy em todas as severidades: 0 vulnerabilidades no sistema e 0 nas dependências Java no momento da última verificação, com Spring Boot 4.0.9 (Spring Framework 7). Novas CVEs surgem com o tempo: mantenha o Dependabot ativo e repita `trivy image` antes de cada release.

## Varredura de segredos

Há um hook de pre-commit com gitleaks (`.pre-commit-config.yaml`, configuração em `.gitleaks.toml`). Para ativar: `pip install pre-commit && pre-commit install`. Para varrer o histórico completo: `gitleaks detect --source .`.

## Limitações conhecidas

- Autenticação HTTP Basic com usuários em memória: sem JWT/mTLS, sem SSO, sem bloqueio por tentativas e sem rate limiting. Em produção, coloque o serviço atrás de um gateway com TLS, rate limiting e autenticação forte.
- O usuário `SERVICE` é verificado com senha em texto simples em memória (sem hash) de propósito, para não custar dezenas de milissegundos por requisição; os usuários `ANALISTA` e `ADMIN` usam BCrypt.
- Sem TLS: o tráfego entre cliente e app é HTTP.
- LocalStack usa credenciais fictícias fixas (`test`/`test`), válidas só para o ambiente local.
- A atualização do perfil do cliente é assíncrona, sem lock e descartada sob sobrecarga; é aproximada por natureza.
- A meta de latência p95 < 100 ms **não** foi atingida nas medições feitas (ver README).
- Não há CodeQL nem varredura de imagem automatizadas no CI; recomenda-se ativar secret scanning, push protection e branch protection no GitHub.
- Tentativas de autenticação com senha errada de `ANALISTA` ou `ADMIN` custam um BCrypt cada, e a diferença de tempo entre `service` (rápido) e os demais pode revelar quais usuários existem. Mitigação esperada: rate limiting no gateway.
- `deviceId`, `location` e `recipientId` são opcionais; omiti-los impede as regras de "novo dispositivo, local ou destinatário" de dispararem. O histórico dessas regras vive no Redis (TTL de 90 dias) e é perdido se o Redis for limpo; o compose limita a memória com `volatile-lru`.
- A idempotência usa só o `transactionId`: reenviar o mesmo id devolve a decisão original, mesmo com valores diferentes. O chamador `SERVICE` é tratado como confiável.
- Há uma conta única por perfil (`analista`, `admin`), então `fraud_case.analyst` não distingue pessoas; alterações de regra não registram o autor.
- Duas atualizações simultâneas de regras diferentes podem receber o mesmo número de versão do conjunto.
- Resolver um caso não altera a decisão já gravada nem notifica sistemas externos.
- Ainda não foi feito teste de intrusão independente. Revisões feitas: auditoria manual, revisão independente por leitura de código, Semgrep, Checkov, actionlint, hadolint, trivy e gitleaks.
