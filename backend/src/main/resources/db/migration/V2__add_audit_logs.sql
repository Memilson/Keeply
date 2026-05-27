CREATE TABLE IF NOT EXISTS audit_logs (
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
