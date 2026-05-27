CREATE TABLE users (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE devices (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    hostname VARCHAR(255) NOT NULL,
    os_name VARCHAR(255),
    device_installation_id VARCHAR(255) NOT NULL,
    refresh_token_hash VARCHAR(255),
    agent_version VARCHAR(255),
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_devices PRIMARY KEY (id),
    CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_devices_user_installation UNIQUE (user_id, device_installation_id)
);

CREATE TABLE protection_plans (
    id UUID NOT NULL,
    device_id UUID NOT NULL,
    plan_type VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_protection_plans PRIMARY KEY (id),
    CONSTRAINT fk_plan_device FOREIGN KEY (device_id) REFERENCES devices (id),
    CONSTRAINT uk_protection_plan_device UNIQUE (device_id)
);

CREATE TABLE protection_plan_sources (
    plan_id UUID NOT NULL,
    source_path VARCHAR(255) NOT NULL,
    CONSTRAINT fk_plan_sources_plan FOREIGN KEY (plan_id) REFERENCES protection_plans (id)
);

CREATE TABLE snapshots (
    id UUID NOT NULL,
    device_id UUID NOT NULL,
    status VARCHAR(255) NOT NULL,
    source_path TEXT,
    total_files BIGINT,
    total_original_size BIGINT,
    total_compressed_size BIGINT,
    manifest_key TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_snapshots PRIMARY KEY (id),
    CONSTRAINT fk_snapshot_device FOREIGN KEY (device_id) REFERENCES devices (id)
);

CREATE TABLE snapshot_files (
    id UUID NOT NULL,
    snapshot_id UUID NOT NULL,
    path TEXT NOT NULL,
    size BIGINT NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE,
    sha256 VARCHAR(64) NOT NULL,
    CONSTRAINT pk_snapshot_files PRIMARY KEY (id),
    CONSTRAINT fk_file_snapshot FOREIGN KEY (snapshot_id) REFERENCES snapshots (id),
    CONSTRAINT uk_snapshot_file_path UNIQUE (snapshot_id, path)
);

CREATE INDEX idx_snapshot_files_snapshot ON snapshot_files (snapshot_id);
CREATE INDEX idx_snapshot_files_path ON snapshot_files (path);

CREATE TABLE file_chunks (
    id UUID NOT NULL,
    snapshot_file_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_hash VARCHAR(64) NOT NULL,
    original_size BIGINT NOT NULL,
    compressed_size BIGINT NOT NULL,
    CONSTRAINT pk_file_chunks PRIMARY KEY (id),
    CONSTRAINT fk_chunk_file FOREIGN KEY (snapshot_file_id) REFERENCES snapshot_files (id),
    CONSTRAINT uk_file_chunks_order UNIQUE (snapshot_file_id, chunk_index)
);

CREATE INDEX idx_file_chunks_file ON file_chunks (snapshot_file_id);
CREATE INDEX idx_file_chunks_hash ON file_chunks (chunk_hash);

CREATE TABLE chunks (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    hash VARCHAR(64) NOT NULL,
    original_size BIGINT,
    compressed_size BIGINT,
    storage_key TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_chunks PRIMARY KEY (id),
    CONSTRAINT uk_chunks_user_hash UNIQUE (user_id, hash)
);

CREATE TABLE transfer_sessions (
    id UUID NOT NULL,
    type VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    device_id UUID NOT NULL,
    snapshot_id UUID NOT NULL,
    minio_access_key VARCHAR(255),
    expires_at TIMESTAMP WITH TIME ZONE,
    last_renewed_at TIMESTAMP WITH TIME ZONE,
    staging_prefix TEXT,
    closed_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_transfer_sessions PRIMARY KEY (id)
);

CREATE INDEX idx_transfer_session_expiry ON transfer_sessions (status, expires_at);
CREATE INDEX idx_transfer_session_snapshot ON transfer_sessions (snapshot_id);

CREATE TABLE restore_jobs (
    id UUID NOT NULL,
    snapshot_id UUID NOT NULL,
    status VARCHAR(255) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_restore_jobs PRIMARY KEY (id),
    CONSTRAINT fk_restore_snapshot FOREIGN KEY (snapshot_id) REFERENCES snapshots (id)
);

CREATE TABLE audit_logs (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    device_id UUID,
    event_type VARCHAR(255) NOT NULL,
    message TEXT,
    metadata_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_audit_device FOREIGN KEY (device_id) REFERENCES devices (id)
);
