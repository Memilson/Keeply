ALTER TABLE transfer_sessions
    ADD COLUMN IF NOT EXISTS minio_access_key VARCHAR(255);
