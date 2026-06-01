-- Add schedule, CDP and encryption settings to protection_plans
ALTER TABLE protection_plans
    ADD COLUMN IF NOT EXISTS cdp_enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS encryption_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS schedule_cron            VARCHAR(100),
    ADD COLUMN IF NOT EXISTS encryption_password_hash VARCHAR(255);
