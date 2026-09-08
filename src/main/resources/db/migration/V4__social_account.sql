CREATE TABLE social_account (
    id               UUID PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    platform         VARCHAR(32)  NOT NULL CHECK (platform IN ('INSTAGRAM', 'TIKTOK', 'YOUTUBE_SHORTS')),
    external_id      VARCHAR(255) NOT NULL,
    username         VARCHAR(255),
    display_name     VARCHAR(255),
    avatar_url       VARCHAR(1024),
    followers        BIGINT,
    access_token     TEXT         NOT NULL,
    refresh_token    TEXT,
    token_expires_at TIMESTAMP(6) WITH TIME ZONE,
    scopes           VARCHAR(512),
    status           VARCHAR(32)  NOT NULL CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED')),
    connected_at     TIMESTAMP(6) WITH TIME ZONE,
    updated_at       TIMESTAMP(6) WITH TIME ZONE,
    last_synced_at   TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uq_social_account_platform_external UNIQUE (platform, external_id)
);
CREATE INDEX idx_social_account_user_id ON social_account (user_id);
CREATE INDEX idx_social_account_status ON social_account (status);
