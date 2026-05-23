# Keeply Java 25 Starter

Starter kit do MVP definido na arquitetura:

```text
Agente JavaFX -> Spring Boot Backend -> PostgreSQL + MinIO <- React Dashboard
```

Este pacote entrega a base inicial em Java 25:

- Backend Spring Boot com JWT, usuários, dispositivos, snapshots, chunks e MinIO.
- Agente JavaFX com telas mínimas e núcleo de backup/restore.
- Content-Defined Chunking simplificado.
- SHA-256 por chunk e por arquivo.
- Compressão GZIP.
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

## Plano de proteção por device

- No primeiro login do agente, o usuário deve escolher um plano (`DEFAULT` ou `CUSTOM`) antes de liberar backups.
- O plano é persistido no backend e é a fonte de verdade para `backup.sources`.
- O `agent.yaml` funciona como cache operacional local e é reconciliado a partir do backend.

Endpoints autenticados:

- `GET /api/devices/{deviceId}/plan` retorna `200` com plano ou `404` sem plano.
- `PUT /api/devices/{deviceId}/plan` faz upsert do plano para o device.

## Contrato de configuração YAML

Linux default: `~/.config/keeply/agent.yaml`  
Windows default: `%ProgramData%\Keeply\agent.yaml`  
Override: `--config <path>`

Exemplo:

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
