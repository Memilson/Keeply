ALTER TABLE transfer_sessions
    DROP CONSTRAINT IF EXISTS ck_transfer_session_backup_requires_staging;
