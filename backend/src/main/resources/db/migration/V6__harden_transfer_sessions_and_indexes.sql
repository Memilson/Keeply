ALTER TABLE transfer_sessions
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE transfer_sessions
    DROP COLUMN IF EXISTS minio_access_key;

CREATE INDEX IF NOT EXISTS idx_transfer_session_user ON transfer_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_transfer_session_device ON transfer_sessions (device_id);
CREATE INDEX IF NOT EXISTS idx_transfer_session_user_device_status ON transfer_sessions (user_id, device_id, status);
CREATE INDEX IF NOT EXISTS idx_snapshots_device_status_created ON snapshots (device_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_restore_jobs_snapshot ON restore_jobs (snapshot_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_transfer_session_user') THEN
        ALTER TABLE transfer_sessions
            ADD CONSTRAINT fk_transfer_session_user FOREIGN KEY (user_id) REFERENCES users (id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_transfer_session_device') THEN
        ALTER TABLE transfer_sessions
            ADD CONSTRAINT fk_transfer_session_device FOREIGN KEY (device_id) REFERENCES devices (id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_transfer_session_snapshot') THEN
        ALTER TABLE transfer_sessions
            ADD CONSTRAINT fk_transfer_session_snapshot FOREIGN KEY (snapshot_id) REFERENCES snapshots (id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_transfer_session_type') THEN
        ALTER TABLE transfer_sessions
            ADD CONSTRAINT ck_transfer_session_type CHECK (type IN ('BACKUP_UPLOAD', 'RESTORE_READ'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_transfer_session_status') THEN
        ALTER TABLE transfer_sessions
            ADD CONSTRAINT ck_transfer_session_status
            CHECK (status IN ('OPEN', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_transfer_session_open_requires_expiry') THEN
        ALTER TABLE transfer_sessions
            ADD CONSTRAINT ck_transfer_session_open_requires_expiry CHECK (status <> 'OPEN' OR expires_at IS NOT NULL);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_transfer_session_renew_before_expiry') THEN
        ALTER TABLE transfer_sessions
            ADD CONSTRAINT ck_transfer_session_renew_before_expiry
            CHECK (expires_at IS NULL OR last_renewed_at IS NULL OR last_renewed_at <= expires_at);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_transfer_session_backup_requires_staging') THEN
        ALTER TABLE transfer_sessions
            ADD CONSTRAINT ck_transfer_session_backup_requires_staging
            CHECK (type <> 'BACKUP_UPLOAD' OR staging_prefix IS NOT NULL);
    END IF;
END $$;
