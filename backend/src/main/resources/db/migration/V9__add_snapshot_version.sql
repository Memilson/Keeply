-- VULN-018: adicionar coluna version para optimistic locking em snapshots
-- Consistente com transfer_sessions que já tem @Version desde V6
ALTER TABLE snapshots
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
