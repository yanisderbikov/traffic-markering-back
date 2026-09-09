CREATE TABLE application_view_snapshot (
    id             BIGSERIAL PRIMARY KEY,
    application_id UUID                        NOT NULL REFERENCES application (id) ON DELETE CASCADE,
    captured_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    views          BIGINT                      NOT NULL CHECK (views >= 0),
    source         VARCHAR(32)                 NOT NULL
);
CREATE INDEX idx_view_snapshot_application ON application_view_snapshot (application_id, captured_at DESC);
