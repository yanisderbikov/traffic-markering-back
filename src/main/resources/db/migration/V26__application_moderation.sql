ALTER TABLE application
    ADD COLUMN moderated_at     TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN moderated_by     BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN rejection_reason TEXT;

CREATE INDEX idx_application_status_created_at ON application (status, created_at);
