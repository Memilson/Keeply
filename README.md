# Keeply Java 25 Starter

Starter kit do MVP de backup com agente Java e backend Spring Boot.

## Arquitetura (atualizada)

```text
Keeply Agent (JavaFX + Daemon) -> Backend Spring Boot -> PostgreSQL + MinIO
```

Fluxo principal:

1. O agente autentica no backend e abre uma `transfer_session`.
2. O agente executa scan + chunking + compressÃ£o e envia objetos para staging no MinIO.
3. O backend audita manifesto/chunks, promove para storage definitivo e finaliza o snapshot.
4. No restore, o agente recebe credenciais temporÃ¡rias read-only e reconstrÃ³i os arquivos.

DocumentaÃ§Ã£o detalhada:

- [Arquitetura do Agente](docs/agent.md)
- [Arquitetura do Backend](docs/backend.md)
- [Arquitetura do Banco](docs/database.md)
- [Arquitetura MinIO](docs/minio.md)

Resumo dos gargalos e legados atuais:

- PromoÃ§Ã£o de chunks no backend com alto fan-out (`exists + copy + save` por chunk).
- Polling de status de auditoria no agente (loop de `listSnapshots`).
- Lacunas de constraints/Ã­ndices no schema (`transfer_sessions` sem FKs).
- Compatibilidades legadas no agente (auth store V1/plaintext e migraÃ§Ã£o tardia de cache local).
- Rate limit em memÃ³ria local (nÃ£o distribuÃ­do).

## EvoluÃ§Ã£o recomendada (concisa)

1. Hardening imediato: auth/rate-limit, escopo de credenciais MinIO, FKs/Ã­ndices crÃ­ticos.
2. RemoÃ§Ã£o de legado: compatibilidade antiga no agente e APIs internas duplicadas.
3. OtimizaÃ§Ã£o: pipeline de auditoria/promoÃ§Ã£o mais idempotente, menos roundtrips e melhor observabilidade.

Este pacote entrega a base inicial em Java 25:

- Backend Spring Boot com JWT, usuÃ¡rios, dispositivos, snapshots, chunks e MinIO.
- Agente JavaFX com telas mÃ­nimas e nÃºcleo de backup/restore.
- Content-Defined Chunking simplificado.
- SHA-256 por chunk e por arquivo.
- CompressÃ£o Zstandard (Zstd), nÃ­vel 3.
- Manifesto JSON.
- Docker Compose para PostgreSQL, MinIO, backend e frontend.

## Rodando a infra

```bash
cd infra
docker compose up -d
```

ServiÃ§os expostos:

- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- MinIO API: `http://localhost:9000`
- MinIO Console: `http://localhost:9001`
- PostgreSQL: `localhost:5432`

Se alguma porta jÃ¡ estiver ocupada, vocÃª pode sobrescrever na hora de subir, por exemplo:

```bash
FRONTEND_PORT=3001 BACKEND_PORT=8081 docker compose up -d
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

ExecuÃ§Ã£o local com config explÃ­cita:

```bash
./gradlew :agent:runDaemon -PdaemonArgs="--config /caminho/agent.yaml"
```

O daemon executa:

1. Aguarda prÃ³ximo horÃ¡rio de `schedule.cron` por padrÃ£o.
2. Opcional: backup imediato no startup com `schedule.runOnStartup: true`.
3. Sem concorrÃªncia entre execuÃ§Ãµes (tick sobreposto Ã© ignorado e logado).
4. A UI JavaFX mostra status/instruÃ§Ãµes e permite "tentar start local" manualmente (fallback para dev).
5. O daemon segue ativo mesmo apÃ³s fechar a UI.

## Corte Para Zstd

A troca de GZIP para Zstd e do perfil CDC para `1 MB / 4 MB / 8 MB` e destrutiva. Snapshots, chunks,
objetos MinIO e caches locais antigos nÃ£o podem ser restaurados nem auditados por esta versÃ£o.

Antes do primeiro backup com Zstd:

1. Pare agente/daemon e backend.
2. Execute `./debug/reset_env.sh`; ele remove volumes PostgreSQL/MinIO e dados locais do agente.
3. Suba o backend atualizado e registre ou autentique novamente o agente.
4. Inicie um novo backup; apenas objetos `.zst` serao produzidos.

## ConfiguraÃ§Ã£o do Backend (.env)

O backend utiliza variÃ¡veis de ambiente para configuraÃ§Ã£o. VocÃª pode criar um arquivo `.env` na raiz do projeto (ou no diretÃ³rio `backend/`) baseando-se no `.env.example`:

```bash
cp .env.example .env
```

Principais variÃ¡veis:
- `SPRING_DATASOURCE_URL`: URL de conexÃ£o com o PostgreSQL.
- `KEEPLY_JWT_SECRET`: Chave secreta para assinatura dos tokens JWT.
- `KEEPLY_MINIO_ENDPOINT`: URL da API do MinIO.
- `KEEPLY_MINIO_ACCESS_KEY` / `KEEPLY_MINIO_SECRET_KEY`: Credenciais do MinIO.

## Contrato de configuraÃ§Ã£o YAML

O agente busca a configuraÃ§Ã£o nos seguintes locais padrÃµes:

- **Linux:** `~/.config/keeply/agent.yaml`
- **Windows:** `%APPDATA%\keeply\agent.yaml`
- **Override:** `--config <path>`

### LocalizaÃ§Ã£o de Logs e Dados (Agente)

| Tipo | Linux | Windows |
| :--- | :--- | :--- |
| **ConfiguraÃ§Ã£o** | `~/.config/keeply/` | `%APPDATA%\keeply\` |
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

Campos obrigatÃ³rios:

- `backend.url`
- `auth.token` ou `auth.email` + `auth.password`
- `backup.sources` (lista nÃ£o vazia de diretÃ³rios existentes)
- `schedule.cron` (formato cron UNIX com 5 campos)

## Linux (systemd)

Arquivos:

- `scripts/linux/keeply-agent.service`
- `scripts/linux/install-systemd.sh`
- `scripts/linux/start-daemon.sh` (execuÃ§Ã£o manual em dev)

InstalaÃ§Ã£o:

```bash
./gradlew :agent:daemonStartScripts
sudo scripts/linux/install-systemd.sh
```

O launcher Ã© gerado em `agent/build/daemon/bin/keeply-agent-daemon` e deve ser publicado em `/opt/keeply/bin/keeply-agent-daemon`.

OperaÃ§Ã£o:

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

## Fluxo mÃ­nimo de teste

1. Suba PostgreSQL e MinIO.
2. Rode o backend.
3. Rode o agente.
4. FaÃ§a register/login pelo endpoint ou ajuste para criar usuÃ¡rio via React depois.
5. Escolha uma pasta no agente.
6. Execute backup.
7. Restaure usando o snapshot gerado.

## ObservaÃ§Ã£o

Este Ã© um starter tÃ©cnico. Ainda faltam:

- React frontend.
- Testes automatizados.
- RetenÃ§Ã£o automÃ¡tica completa.
- Parser persistente do manifesto em `snapshot_files` e `file_chunks`.
- Criptografia ponta a ponta.
- Endurecimento de seguranÃ§a.

## ?? Arquitetura Mobile (Artigo Técnico)

O novo aplicativo companheiro **Keeply Mobile** atua como um controle remoto seguro para a sua nuvem de backups, construído com foco primário em estabilidade e segurança:

1. **Arquitetura Pura (MVC)**: O app foi totalmente isolado em uma arquitetura declarativa usando Provider. O estado é gerenciado globalmente pelos *Controllers* (como FilesController e AuthController), mantendo as *Views* focadas apenas em renderização.
2. **Deep Search (Busca Profunda)**: A busca do aplicativo não é uma busca local ingênua; ela se comunica com o backend, percorrendo os manifestos do banco de dados para encontrar arquivos perdidos *dentro* de múltiplos snapshots com altíssima performance.
3. **Resiliência de Sessão**: Implementamos injeção de SecureStorage para reter o Token JWT, atrelado a um sistema interceptador (Interceptor) que fará o kick limpo do usuário se o servidor expirar a sessão, mantendo a integridade dos dados locais.

---

## ??? Primeiros Passos para Novos Desenvolvedores (Clonando o Projeto)

Ao clonar este repositório, você notará que ele está blindado. Siga os passos abaixo para compilar a aplicação na sua máquina:

### 1. Configurando o Backend / Docker
As credenciais do banco de dados e as chaves JWT não estão no repositório.
1. Na raiz do projeto, duplique o arquivo .env.example e renomeie a cópia para .env.
2. Preencha as chaves change-me com suas próprias senhas e secrets locais.
3. Rode docker-compose -f infra/docker-compose.yml up -d para subir o banco (agora resiliente com healthchecks!).

### 2. Configurando o Aplicativo Mobile
Os IPs estáticos e tokens de Firebase não sobem no GitHub.
1. Acesse mobile/lib/core/constants/.
2. Duplique o arquivo env_config.example.dart e renomeie-o para env_config.dart.
3. Abra `env_config.dart` e altere a URL `http://SEU_IP:8080` para apontar para a máquina onde o seu Backend está rodando.
   > **Aviso sobre Testes Locais:** Se o seu backend estiver rodando apenas na sua máquina local (sem um servidor na nuvem), você **deve** usar o seu IP local (ex: `192.168.x.x`) neste arquivo, e o celular precisará estar conectado na **mesma rede Wi-Fi** que o servidor para os backups e navegação funcionarem!
4. Crie o arquivo `local.properties` em `mobile/android/local.properties` contendo o caminho do seu SDK do Android se você for rodar via linha de comando puro.

Feito isso, o projeto já pode ser testado normalmente:
```bash
cd mobile
flutter pub get
flutter run
```

## Android (Mobile App)

O aplicativo companheiro permite listar backups e arquivos da nuvem.

Build e Instalação:

```bash
# Entre no diretório mobile
cd mobile

# Gere o pacote Release APK
flutter build apk --release

# Instale no dispositivo via ADB
flutter install
```

O APK gerado ficará disponível em `mobile\build\app\outputs\flutter-apk\Keeply.apk` e pode ser enviado ou transferido manualmente para qualquer aparelho Android.
