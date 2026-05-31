# Arquitetura do Agente

## Escopo

O agente (`agent/`) executa backup e restore, com UI JavaFX e modo daemon.

## Componentes principais

- `KeeplyAgentApp`: entrada da UI.
- `KeeplyAgentDaemonApp` + `BackupCycleRunner`: execução headless agendada.
- `BackupEngine`: orquestra scan, chunking, deduplicação, upload e finalize.
- `RestoreEngine`: restauração por manifesto + validação de hash.
- `DirectTransferStorage`: acesso direto ao MinIO com credenciais temporárias.
- `LocalDatabase` + `core/db`: cache local SQLite.

## Fluxo resumido

1. Login/refresh no backend e abertura de sessão de transferência.
2. Scan de arquivos e CDC (`ContentDefinedChunker`).
3. Compressão Zstd, hash, deduplicação e upload no MinIO staging.
4. Envio do manifesto e `completeSnapshot`.
5. Polling até status final do snapshot.

## Gargalos atuais

- Polling de auditoria por `listSnapshots` em loop.
- Duas passagens em arquivos alterados no backup (hash e depois upload).
- Lock em renovação de credencial em `DirectTransferStorage`.
- Classe de UI concentrando responsabilidades demais.

## Legados para remover

- Compatibilidade V1/plaintext no `DeviceAuthStore` (após janela de migração).
- Migração tardia de `chunks_json` no cache local.
- `BackendClient` monolítico e overloads duplicados em listagem de arquivos.
- Fallback ambíguo para `./agent.yaml` no CWD.

## Melhorias objetivas

1. Desacoplar UI de orquestração operacional.
2. Substituir polling por sinalização/push de status (ou backoff progressivo).
3. Tornar restore atômico por arquivo (escrita temporária + rename).
4. Aumentar testes de CDC, restore e fluxo completo de backup.
