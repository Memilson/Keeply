# Keeply Agent C++ Base

Base MVP de agente de backup em C++ para Windows/Linux, sem web e sem mobile.

O projeto entrega:

- CLI `keeply-agent`
- Daemon separado `keeply-daemon`
- GUI desktop nativa `keeply-gui`
- Interface terminal `ui`
- Jobs locais com intervalo e retenção
- Modo daemon contínuo
- Backup file-level por chunks fixos de 4 MiB
- SHA-256 por chunk e por arquivo
- Compressão ZSTD dos chunks e manifestos
- Criptografia client-side opcional AES-256-GCM
- Manifest JSON versionado
- SQLite local para snapshots/chunks
- Storage local para teste
- Storage S3/MinIO via HTTP + AWS Signature V4
- Verify de snapshots sem restore
- Prune por retenção
- Restore com validação de hash de chunk
- Dockerfile + docker-compose para MinIO
- Stubs para VSS/USN no Windows

> Isto é uma base funcional de MVP. Não é Acronis completo: ainda não tem VSS completo, USN Journal completo, CBT, CDP, disk image, driver ou bare-metal restore.

## Arquitetura

```text
source files
  -> chunker fixed 4 MiB
  -> SHA-256
  -> ZSTD compress
  -> AES-256-GCM opcional
  -> object store local/S3/MinIO
  -> manifest json.zst
  -> SQLite local
```

## Build com vcpkg

### Dependências

```bash
vcpkg install zstd sqlite3 openssl curl nlohmann-json
```

### Windows PowerShell

```powershell
cmake -S . -B build -DCMAKE_TOOLCHAIN_FILE=C:/vcpkg/scripts/buildsystems/vcpkg.cmake
cmake --build build --config Release
```

Binário:

```powershell
.\build\Release\keeply-agent.exe
.\build\Release\keeply-daemon.exe
.\build\Release\keeply-gui.exe
```

### Linux

```bash
cmake -S . -B build -DCMAKE_TOOLCHAIN_FILE=$VCPKG_ROOT/scripts/buildsystems/vcpkg.cmake
cmake --build build -j
```

## Subir MinIO

```bash
cd infra/minio
docker compose up -d --build
```

Console:

```text
http://localhost:9001
user: keeply
pass: keeply123456
```

S3 endpoint:

```text
http://localhost:9000
bucket: keeply
```

## Usar com storage local

```bash
keeply-agent init-local --config keeply.local.json --repo ./repo --db ./keeply.db
keeply-agent backup --config keeply.local.json --source ./testdata
keeply-agent verify --config keeply.local.json --snapshot latest
keeply-agent list --config keeply.local.json
keeply-agent restore --config keeply.local.json --snapshot latest --target ./restore
```

## Usar com MinIO

```bash
keeply-agent init-s3 \
  --config keeply.minio.json \
  --endpoint http://localhost:9000 \
  --bucket keeply \
  --access-key keeply \
  --secret-key keeply123456 \
  --region us-east-1 \
  --prefix repos/dev-machine \
  --db ./keeply.db

keeply-agent backup --config keeply.minio.json --source ./testdata
keeply-agent list --config keeply.minio.json
keeply-agent verify --config keeply.minio.json --snapshot latest
keeply-agent restore --config keeply.minio.json --snapshot latest --target ./restore
```

## Jobs e agente

```bash
keeply-agent job-add --config keeply.minio.json --name docs --source ./testdata --interval-minutes 60 --keep-last 10
keeply-agent job-list --config keeply.minio.json
keeply-agent run-once --config keeply.minio.json --job docs
keeply-agent status --config keeply.minio.json
keeply-agent events --config keeply.minio.json --limit 20
keeply-daemon --config keeply.minio.json
```

`keeply-daemon` fica em loop, executando jobs habilitados quando o intervalo vencer. Cada job pode aplicar retenção automática. Para uma execução pontual:

```bash
keeply-daemon --config keeply.minio.json --once --job docs
```

O JSON guarda configuracao editavel. O SQLite guarda snapshots, chunks, estado dos jobs, status do daemon e eventos locais.

## GUI desktop

```powershell
.\out\build\x64-debug\keeply-gui.exe keeply.minio.json
```

A GUI permite configurar Local/MinIO, gerenciar jobs, executar backup, iniciar/parar agente, listar snapshots, verificar, restaurar e aplicar retenção.

## Interface terminal

```bash
keeply-agent ui --config keeply.minio.json
```

A interface terminal oferece as mesmas operações principais para uso sem janela.

## Criptografia

```bash
keeply-agent keygen
keeply-agent encryption --config keeply.minio.json --enabled true --key-hex HEX
```

Se `--key-hex` não for enviado, o agente gera uma chave e grava no JSON. Guarde essa chave fora da máquina.

## Retenção manual

```bash
keeply-agent prune --config keeply.minio.json --keep-last 10
```

## Config gerada

```json
{
  "db_path": "./keeply.db",
  "chunk_size": 4194304,
  "compression_level": 3,
  "repository": {
    "type": "s3",
    "endpoint": "http://localhost:9000",
    "bucket": "keeply",
    "access_key": "keeply",
    "secret_key": "keeply123456",
    "region": "us-east-1",
    "prefix": "repos/dev-machine"
  },
  "encryption": {
    "enabled": false,
    "key_hex": ""
  },
  "agent": {
    "poll_seconds": 30
  },
  "jobs": [],
  "windows": {
    "use_vss": false,
    "use_usn": false
  }
}
```

## Próximos módulos reais

1. Implementar VSS requester no Windows.
2. Implementar USN Journal incremental.
3. Implementar serviço Windows nativo.
4. Implementar Linux systemd + inotify/fanotify.
5. Depois: image-level backup e CBT.

## Limitações conhecidas

- Chunking atual é fixo, não content-defined.
- Sem VSS real ainda; o backup lê arquivos diretamente.
- Sem preservação completa de ACL/ADS/reparse points.
- S3 usa path-style; ideal para MinIO. AWS S3 pode exigir ajustes conforme região/bucket.
- Sem multipart upload ainda; para arquivos grandes, implementar multipart depois.
