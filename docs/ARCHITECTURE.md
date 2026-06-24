# Keeply Agent C++ Architecture

## Objetivo

Criar uma base C++ para agente de backup Windows/Linux sem frontend web e sem mobile. O sistema opera por CLI, daemon, GUI desktop nativa e interface terminal, usando SQLite local e S3/MinIO como repositório remoto.

## Fluxo de backup

```text
backup source
  -> scanner
  -> chunker
  -> sha256
  -> zstd
  -> crypto opcional
  -> object store
  -> manifest
  -> sqlite
```

## Componentes

### Core

- `BackupEngine`: executa scan, chunk, compressão, upload e manifesto.
- `RestoreEngine`: lê manifesto, baixa chunks, descomprime e valida hash.
- `VerifyEngine`: valida manifestos e chunks sem restaurar em disco.
- `PruneEngine`: aplica retenção e remove objetos sem referência.
- `AgentRunner`: executa jobs em loop ou uma vez.
- `LocalDb`: SQLite local para snapshots, chunks e estado.
- `Hash`: SHA-256 e HMAC-SHA256.
- `Compression`: ZSTD.
- `Crypto`: AES-256-GCM opcional antes do upload.

### Apps

- `keeply-agent`: CLI/admin para init, jobs, backup manual, list, verify, prune, restore e interface terminal.
- `keeply-daemon`: processo separado para executar jobs continuamente ou uma vez.
- `keeply-gui`: GUI Win32 nativa para configurar storage, jobs, backup, restore, verify e prune.
- `InteractiveShell`: interface terminal para operações básicas do CLI.

### Storage

- `FsObjectStore`: repositório local para teste.
- `S3ObjectStore`: PUT/GET/HEAD em S3/MinIO com AWS Signature V4.

### Platform

- `platform/windows/VssSnapshot`: stub para VSS.
- `platform/windows/UsnJournal`: stub para USN Journal.

## Processos

```text
keeply-agent
  -> CLI/admin
  -> init, job-add, list, backup manual, restore, verify, prune, ui

keeply-daemon
  -> AgentRunner
  -> jobs em loop ou --once

keeply-gui
  -> GUI Win32
  -> fase atual ainda executa operacoes diretas
  -> fase seguinte deve falar com daemon por IPC local
```

## VSS

VSS deve ser usado no Windows para criar snapshot consistente antes da leitura. O fluxo real deve usar `IVssBackupComponents`.

## USN Journal

USN Journal deve ser usado para incremental por arquivo: depois do primeiro backup completo, o agente consulta mudanças desde o último USN salvo em SQLite.

## CBT/CDP

CBT real exige tracking de blocos alterados. Em máquina física Windows, isso normalmente pede driver/filtro. Não faz parte do MVP.

CDP real também exige journaling contínuo. Para MVP, usar watcher/debounce e snapshots frequentes.

## Limite atual

O agente atual faz backup file-level. VSS e USN ainda não executam fluxo real. Disk image, bare-metal restore e CBT ficam para fases posteriores.
