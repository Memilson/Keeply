# Arquitetura do Banco

## Escopo

O banco principal é PostgreSQL com schema inicial em `V1__initial_schema.sql` e evoluções por Flyway.

## Entidades centrais

- `users`, `devices`: identidade e vínculo de dispositivos.
- `snapshots`, `snapshot_files`, `file_chunks`: catálogo lógico de backup.
- `chunks`: deduplicação por hash (`user_id + hash`).
- `transfer_sessions`: sessão temporária de upload/restore.
- `audit_logs`: trilha de eventos.

## Gargalos e riscos atuais

- `transfer_sessions` sem FKs para `users/devices/snapshots`.
- Índices ausentes para alguns padrões de join/filtro (`snapshots.device_id`, `restore_jobs.snapshot_id`).
- Migração redundante de `audit_logs` (V1 já cria e V2 recria).
- Alguns campos críticos sem `NOT NULL`/`CHECK` de domínio.

## Legados para remover

- Histórico de migration com sobreposição de responsabilidade.
- Campos e constraints permissivos demais para estado atual do sistema.

## Melhorias objetivas

1. Adicionar FKs e índices críticos em `transfer_sessions`, `snapshots` e `restore_jobs`.
2. Revisar constraints (`CHECK`/`NOT NULL`) para status/tipos e metadados de compressão.
3. Consolidar migrations para reduzir ambiguidade operacional.
4. Validar política de `baseline-on-migrate` fora de ambiente local.
