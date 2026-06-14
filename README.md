# Keeply

Keeply é uma solução de backup em nuvem com agente desktop, API backend, painel web, landing page e aplicativo mobile. O projeto implementa backup por snapshots, deduplicação por chunks, compressão Zstandard, armazenamento S3 compatível via MinIO, restauração de arquivos e um assistente de IA integrado ao painel.

O repositório está organizado como um MVP técnico de produto SaaS. A parte de backup é engenharia de armazenamento; a funcionalidade de IA é o **Keeply I.A**, um assistente operacional que responde dúvidas sobre backups, máquinas, snapshots, restauração, segurança e diagnóstico dentro do painel web.

## Visão rápida

```text
Agente JavaFX/Daemon
        |
        | REST + JWT + credenciais temporárias MinIO
        v
Backend Spring Boot
        |
        +--> PostgreSQL: usuários, dispositivos, snapshots, arquivos, chunks e sessões
        |
        +--> MinIO: chunks comprimidos e manifestos
        |
        +--> OpenRouter: Keeply I.A
        |
        +--> Prometheus/Grafana: métricas locais

Frontend Next.js / Mobile Flutter
        |
        +--> API REST do backend
```

## Componentes

| Diretório | Função |
| --- | --- |
| `backend/` | API REST em Spring Boot. Controla autenticação, dispositivos, snapshots, sessões de transferência, manifestos, downloads e chat de IA. |
| `agent/` | Agente desktop em JavaFX e modo daemon. Executa scan, chunking, compressão, upload e restore. |
| `frontend/` | Painel web em Next.js. Exibe dashboard, máquinas, backups, proteção, atividades e Keeply I.A. |
| `landing/` | Site público/landing page em Next.js. |
| `mobile/` | Aplicativo Flutter para consulta remota de backups, snapshots, arquivos e configurações. |
| `infra/` | Docker Compose local e Compose de produção. |
| `docs/` | Documentação técnica, produção, banco, MinIO, mobile, IA e entrega N2. |
| `debug/` | Scripts de reset do ambiente local. |

## Stack principal

| Camada | Tecnologias |
| --- | --- |
| Backend | Java 25, Spring Boot 4.0.6, Spring Security, JPA, Flyway, JJWT, Caffeine, Micrometer/Prometheus |
| Agente | Java 25, JavaFX 21, SQLite, MinIO SDK, Zstd JNI, cron-utils |
| Frontend | Next.js 16, React 19, TypeScript, Tailwind CSS 4 |
| Landing | Next.js 16, React 19, TypeScript, Tailwind CSS 4 |
| Mobile | Flutter/Dart, Provider, HTTP, Secure Storage, File Picker, Permission Handler |
| Infra | PostgreSQL 16, MinIO, Prometheus, Grafana, Nginx em produção |
| IA | OpenRouter Chat Completions, modelo padrão `nvidia/nemotron-3-super-120b-a12b:free` |

## Funcionalidades implementadas

### Backup e snapshots

- Registro/autenticação de usuário e dispositivo.
- Plano de proteção por dispositivo.
- Scan de diretórios configurados no agente.
- Content-Defined Chunking com perfil atual de `1 MB / 4 MB / 8 MB`.
- Hash SHA-256 por arquivo e por chunk.
- Compressão Zstandard nível 3.
- Deduplicação por hash de chunk por usuário.
- Manifesto de snapshot em JSON comprimido.
- Upload direto para MinIO com credenciais temporárias.
- Sessões de transferência renováveis para backup e restore.
- Auditoria/processamento do snapshot no backend.
- Listagem paginada de arquivos por snapshot.
- Download de arquivo, pasta ou seleção de arquivos.

### Painel web

- Login e registro.
- Dashboard com saúde do ambiente e atividade de backups.
- Tela de máquinas/dispositivos.
- Tela de backups e navegação por snapshot.
- Tela de proteção com fontes, agendamento, retenção, validação e criptografia.
- Tela de atividades.
- Keeply I.A integrado ao painel.

### Mobile

- Login contra backend configurável.
- Consulta de dispositivos, snapshots e arquivos.
- Busca profunda de arquivos dentro de snapshots via backend.
- Download/preview de arquivos conforme suporte do app.
- Armazenamento seguro de sessão.

### Observabilidade

- Actuator health.
- Endpoint Prometheus no backend.
- Postgres Exporter no Compose local.
- Grafana provisionado com datasource Prometheus no Compose local.

### Keeply I.A

O Keeply I.A é um chat operacional do painel web. Ele não executa backup, não altera dados e não consulta o estado real do ambiente por conta própria. O fluxo atual é:

1. Usuário abre o botão **Keeply I.A** no painel web.
2. O frontend envia `message` e até 8 mensagens de histórico para `POST /api/ai/chat`.
3. O backend valida a requisição e chama o OpenRouter.
4. O serviço usa um prompt de sistema focado em suporte a backups, snapshots, restauração e diagnóstico.
5. O backend retorna `{ answer, model }`.
6. O frontend exibe a resposta no chat.

Detalhes específicos estão em [`docs/ia.md`](docs/ia.md) e o material para a atividade N2 está em [`docs/n2-atividade-final-ia.md`](docs/n2-atividade-final-ia.md).

## Documentação

- [`docs/agent.md`](docs/agent.md): agente desktop/daemon.
- [`docs/backend.md`](docs/backend.md): API, serviços e segurança do backend.
- [`docs/database.md`](docs/database.md): schema PostgreSQL e migrações.
- [`docs/minio.md`](docs/minio.md): organização do object storage.
- [`docs/mobile.md`](docs/mobile.md): arquitetura do aplicativo Flutter.
- [`docs/ia.md`](docs/ia.md): funcionalidade de IA.
- [`docs/curl.md`](docs/curl.md): chamadas úteis com `curl`.
- [`docs/deploy-cloud.md`](docs/deploy-cloud.md): implantação em nuvem.
- [`docs/production.md`](docs/production.md): Compose de produção.
- [`docs/progresso.md`](docs/progresso.md): status atual, limitações e próximos passos.
- [`docs/n2-atividade-final-ia.md`](docs/n2-atividade-final-ia.md): relatório objetivo para a entrega N2.
- [`docs/roteiro-video-n2.md`](docs/roteiro-video-n2.md): roteiro prático para gravação do vídeo.

## Requisitos locais

- JDK 25 disponível no ambiente.
- Docker e Docker Compose.
- Node.js compatível com Next.js 16.
- Flutter instalado, caso vá rodar o app mobile.
- Chave OpenRouter se for testar o Keeply I.A.

## Configuração de ambiente

Copie o template:

```bash
cp .env.example .env
```

Preencha pelo menos:

```dotenv
POSTGRES_DB=keeply
POSTGRES_USER=keeply
POSTGRES_PASSWORD=keeply123
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/keeply
SPRING_DATASOURCE_USERNAME=keeply
SPRING_DATASOURCE_PASSWORD=keeply123

KEEPLY_JWT_SECRET=troque-por-uma-chave-com-32-caracteres-ou-mais
KEEPLY_MASTER_KEY=troque-por-uma-chave-hexadecimal-segura-com-64-caracteres

MINIO_ROOT_USER=keeply
MINIO_ROOT_PASSWORD=keeply123
KEEPLY_MINIO_ENDPOINT=http://localhost:9000
KEEPLY_MINIO_ACCESS_KEY=keeply
KEEPLY_MINIO_SECRET_KEY=keeply123
KEEPLY_MINIO_BUCKET=keeply

KEEPLY_AI_BASE_URL=https://openrouter.ai/api/v1
KEEPLY_AI_MODEL=nvidia/nemotron-3-super-120b-a12b:free
KEEPLY_AI_API_KEY=sk-or-v1-sua-chave-openrouter
KEEPLY_AI_TITLE=Keeply
KEEPLY_AI_REFERER=http://localhost:3000
```

Sem `KEEPLY_AI_API_KEY`, o chat de IA retorna erro controlado informando que a IA não está configurada.

## Rodando com Docker Compose local

```bash
cd infra
docker compose --env-file ../.env up -d --build
```

Serviços locais:

| Serviço | URL |
| --- | --- |
| Frontend | `http://localhost:3000` |
| Backend | `http://localhost:8080` |
| MinIO API | `http://localhost:9000` |
| MinIO Console | `http://localhost:9001` |
| PostgreSQL | `localhost:5432` |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3001` |

Credenciais padrão de desenvolvimento, quando não sobrescritas:

```text
MinIO Console: keeply / keeply123
PostgreSQL: keeply / keeply123
Grafana: admin / admin
```

Para trocar portas sem editar o Compose:

```bash
FRONTEND_PORT=3002 BACKEND_PORT=8081 docker compose --env-file ../.env up -d
```

## Rodando módulos manualmente

Backend:

```bash
./gradlew :backend:bootRun
```

Agente com UI:

```bash
./gradlew :agent:run
```

Agente daemon/headless:

```bash
./gradlew :agent:runDaemon -PdaemonArgs="--config /caminho/agent.yaml"
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Landing:

```bash
cd landing
npm install
npm run dev
```

Mobile:

```bash
cd mobile
flutter pub get
flutter run --dart-define=KEEPLY_BACKEND_BASE_URL=http://10.0.2.2:8080
```

Use `10.0.2.2` no emulador Android. Em celular físico, use o IP da máquina que executa o backend, por exemplo `http://192.168.1.50:8080`.

## Configuração do agente

O agente procura `agent.yaml` nos locais padrão:

| Sistema | Configuração | Dados locais | Logs |
| --- | --- | --- | --- |
| Linux | `~/.config/keeply/agent.yaml` | `~/.local/share/keeply/` | `~/.local/state/keeply/` |
| Windows | `%APPDATA%\keeply\agent.yaml` | `%LOCALAPPDATA%\keeply\` | `%LOCALAPPDATA%\keeply\` |

Também é possível passar o caminho explicitamente:

```bash
./gradlew :agent:runDaemon -PdaemonArgs="--config ./agent.yaml"
```

Exemplo mínimo:

```yaml
backend:
  url: http://localhost:8080

auth:
  email: keeply@keeply.com
  password: keeply123
  # token: "<jwt-opcional>"

device:
  name: workstation-main

backup:
  sources:
    - /home/user/Documents
    - /home/user/Pictures

schedule:
  cron: "*/30 * * * *"
  runOnStartup: false
```

Campos obrigatórios:

- `backend.url`
- `auth.token` ou `auth.email` + `auth.password`
- `backup.sources` com ao menos um diretório existente
- `schedule.cron` com cron UNIX de 5 campos

## Fluxo mínimo de demonstração

1. Subir PostgreSQL, MinIO, backend e frontend.
2. Registrar um usuário.
3. Entrar no painel web.
4. Abrir o Keeply I.A e fazer uma pergunta sobre restauração ou saúde dos backups.
5. Rodar o agente e executar um backup de uma pasta pequena.
6. Conferir snapshot no painel.
7. Navegar pelos arquivos do snapshot.
8. Baixar/restaurar um arquivo.

Para a atividade N2, o vídeo deve priorizar a etapa 4, porque é a funcionalidade de IA exigida.

## Corte destrutivo para Zstd

A versão atual usa Zstandard nível 3 e CDC com perfil `1 MB / 4 MB / 8 MB`. Snapshots antigos criados com GZIP não são compatíveis com esta versão.

Antes do primeiro backup com Zstd em ambiente local antigo:

```bash
./debug/reset_env.sh
```

No Windows:

```powershell
.\debug\reset_env.ps1
```

Esses scripts removem volumes/dados locais de desenvolvimento. Não execute em ambiente com dados reais.

## Produção

A produção usa `infra/docker-compose.prod.yml` com Nginx na frente:

- `https://keeply.app.br/`: landing.
- `https://keeply.app.br/prod`: painel web.
- `https://keeply.app.br/api`: backend.
- `https://keeply.app.br/minio`: proxy S3/MinIO conforme configuração atual do Compose.

Leia [`docs/production.md`](docs/production.md) antes de publicar. Não use senhas de desenvolvimento em produção.

## Limitações conhecidas

- A IA atual é um chat assistivo; não possui RAG nem acesso automático ao estado real do painel.
- Criptografia ponta a ponta dos dados de backup ainda não está completa.
- Retenção automática e limpeza de chunks órfãos ainda precisam de rotina dedicada.
- Algumas rotas de download/restauração dependem da consistência entre banco, manifesto e objetos MinIO.
- O Compose local expõe serviços apenas em `127.0.0.1` por segurança; celular físico precisa de backend acessível na rede.

## Comandos úteis

```bash
# Ver logs locais
cd infra
docker compose logs -f backend

docker compose logs -f frontend

docker compose logs -f minio

# Health do backend
curl -fsS http://localhost:8080/actuator/health

# Prometheus do backend
curl -fsS http://localhost:8080/actuator/prometheus

# Derrubar ambiente local
cd infra
docker compose down
```
