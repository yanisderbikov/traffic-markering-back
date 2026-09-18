ALTER TABLE users DROP COLUMN password;
ALTER TABLE users ADD COLUMN verified_at TIMESTAMP(6) WITH TIME ZONE;
UPDATE users SET verified_at = created_at;

CREATE TABLE login_code (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    code       VARCHAR(6)   NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    attempts   INT          NOT NULL DEFAULT 0,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_login_code_email ON login_code (email);

CREATE TABLE task (
    id         UUID PRIMARY KEY,
    type       VARCHAR(255) NOT NULL,
    payload    TEXT,
    status     VARCHAR(255) NOT NULL,
    comment    TEXT,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_task_type_status_created ON task (type, status, created_at);
