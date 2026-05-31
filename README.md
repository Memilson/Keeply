# Keeply Java 25 Starter

Starter kit do MVP de backup com agente Java e backend Spring Boot.

## Arquitetura (atualizada)

```text
Keeply Agent (JavaFX + Daemon) -> Backend Spring Boot -> PostgreSQL + MinIO
```

Fluxo principal:

1. O agente autentica no backend e abre uma `transfer_session`.
2. O agente executa scan + chunking + compressão e envia objetos para staging no MinIO.
3. O backend audita manifesto/chunks, promove para storage definitivo e finaliza o snapshot.
4. No restore, o agente recebe credenciais temporárias read-only e reconstrói os arquivos.

Documentação detalhada:

- [Arquitetura do Agente](docs/agent.md)
- [Arquitetura do Backend](docs/backend.md)
- [Arquitetura do Banco](docs/database.md)
- [Arquitetura MinIO](docs/minio.md)

Resumo dos gargalos e legados atuais:

- Promoção de chunks no backend com alto fan-out (`exists + copy + save` por chunk).
- Polling de status de auditoria no agente (loop de `listSnapshots`).
- Lacunas de constraints/índices no schema (`transfer_sessions` sem FKs).
- Compatibilidades legadas no agente (auth store V1/plaintext e migração tardia de cache local).
- Rate limit em memória local (não distribuído).

## Evolução recomendada (concisa)

1. Hardening imediato: auth/rate-limit, escopo de credenciais MinIO, FKs/índices críticos.
2. Remoção de legado: compatibilidade antiga no agente e APIs internas duplicadas.
3. Otimização: pipeline de auditoria/promoção mais idempotente, menos roundtrips e melhor observabilidade.

Este pacote entrega a base inicial em Java 25:

- Backend Spring Boot com JWT, usuários, dispositivos, snapshots, chunks e MinIO.
- Agente JavaFX com telas mínimas e núcleo de backup/restore.
- Content-Defined Chunking simplificado.
- SHA-256 por chunk e por arquivo.
- Compressão Zstandard (Zstd), nível 3.
- Manifesto JSON.
- Docker Compose para PostgreSQL e MinIO.

## Rodando a infra

```bash
cd infra
docker compose up -d
```

MinIO Console:

```text
http://localhost:9001
user: keeply
senha: keeply123
```

PostgreSQL:

```text
localhost:5432
database: keeply
user: keeply
password: keeply123
```

## Rodando o backend

```bash
./gradlew :backend:bootRun
```

Backend:

```text
http://localhost:8080
```

## Rodando o agente

```bash
./gradlew :agent:run
```

## Rodando o agente headless (daemon)

Execução local com config explícita:

```bash
./gradlew :agent:runDaemon -PdaemonArgs="--config /caminho/agent.yaml"
```

O daemon executa:

1. Aguarda próximo horário de `schedule.cron` por padrão.
2. Opcional: backup imediato no startup com `schedule.runOnStartup: true`.
3. Sem concorrência entre execuções (tick sobreposto é ignorado e logado).
4. A UI JavaFX mostra status/instruções e permite "tentar start local" manualmente (fallback para dev).
5. O daemon segue ativo mesmo após fechar a UI.

## Corte Para Zstd

A troca de GZIP para Zstd e do perfil CDC para `1 MB / 4 MB / 8 MB` e destrutiva. Snapshots, chunks,
objetos MinIO e caches locais antigos não podem ser restaurados nem auditados por esta versão.

Antes do primeiro backup com Zstd:

1. Pare agente/daemon e backend.
2. Execute `./debug/reset_env.sh`; ele remove volumes PostgreSQL/MinIO e dados locais do agente.
3. Suba o backend atualizado e registre ou autentique novamente o agente.
4. Inicie um novo backup; apenas objetos `.zst` serao produzidos.

## Configuração do Backend (.env)

O backend utiliza variáveis de ambiente para configuração. Você pode criar um arquivo `.env` na raiz do projeto (ou no diretório `backend/`) baseando-se no `.env.example`:

```bash
cp .env.example .env
```

Principais variáveis:
- `SPRING_DATASOURCE_URL`: URL de conexão com o PostgreSQL.
- `KEEPLY_JWT_SECRET`: Chave secreta para assinatura dos tokens JWT.
- `KEEPLY_MINIO_ENDPOINT`: URL da API do MinIO.
- `KEEPLY_MINIO_ACCESS_KEY` / `KEEPLY_MINIO_SECRET_KEY`: Credenciais do MinIO.

## Contrato de configuração YAML

O agente busca a configuração nos seguintes locais padrões:

- **Linux:** `~/.config/keeply/agent.yaml`
- **Windows:** `%APPDATA%\keeply\agent.yaml`
- **Override:** `--config <path>`

### Localização de Logs e Dados (Agente)

| Tipo | Linux | Windows |
| :--- | :--- | :--- |
| **Configuração** | `~/.config/keeply/` | `%APPDATA%\keeply\` |
| **Banco de Dados** | `~/.local/share/keeply/` | `%LOCALAPPDATA%\keeply\` |
| **Logs** | `~/.local/state/keeply/` | `%LOCALAPPDATA%\keeply\` |
| **PID/Runtime** | `/tmp/keeply/` | `%TEMP%\keeply\` |

Exemplo de `agent.yaml`:

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
- `backup.sources` (lista não vazia de diretórios existentes)
- `schedule.cron` (formato cron UNIX com 5 campos)

## Linux (systemd)

Arquivos:

- `scripts/linux/keeply-agent.service`
- `scripts/linux/install-systemd.sh`
- `scripts/linux/start-daemon.sh` (execução manual em dev)

Instalação:

```bash
./gradlew :agent:daemonStartScripts
sudo scripts/linux/install-systemd.sh
```

O launcher é gerado em `agent/build/daemon/bin/keeply-agent-daemon` e deve ser publicado em `/opt/keeply/bin/keeply-agent-daemon`.

Operação:

```bash
sudo systemctl enable --now keeply-agent
sudo systemctl status keeply-agent
journalctl -u keeply-agent -f
```

## Windows (Task Scheduler)

Arquivo:

- `scripts/windows/install-task.ps1`

Criar/atualizar e iniciar:

```powershell
.\gradlew.bat :agent:daemonStartScripts
powershell -ExecutionPolicy Bypass -File .\scripts\windows\install-task.ps1 `
  -TaskName "KeeplyAgent" `
  -KeeplyHome "C:\Keeply" `
  -ConfigPath "$env:ProgramData\Keeply\agent.yaml" `
  -LogPath "$env:ProgramData\Keeply\agent.log"
```

No Windows, publique `agent\build\daemon\bin\keeply-agent-daemon.bat` em `C:\Keeply\bin\keeply-agent-daemon.bat`.

Consultar:

```powershell
schtasks /Query /TN KeeplyAgent /V /FO LIST
```

## Fluxo mínimo de teste

1. Suba PostgreSQL e MinIO.
2. Rode o backend.
3. Rode o agente.
4. Faça register/login pelo endpoint ou ajuste para criar usuário via React depois.
5. Escolha uma pasta no agente.
6. Execute backup.
7. Restaure usando o snapshot gerado.

## Observação

Este é um starter técnico. Ainda faltam:

- React frontend.
- Testes automatizados.
- Retenção automática completa.
- Parser persistente do manifesto em `snapshot_files` e `file_chunks`.
- Criptografia ponta a ponta.
- Endurecimento de segurança.
