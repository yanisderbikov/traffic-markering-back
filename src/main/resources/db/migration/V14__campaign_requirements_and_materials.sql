ALTER TABLE campaign
    ADD COLUMN min_video_seconds      INTEGER CHECK (min_video_seconds > 0),
    ADD COLUMN min_paid_views         BIGINT CHECK (min_paid_views > 0),
    ADD COLUMN max_videos_per_creator INTEGER CHECK (max_videos_per_creator > 0),
    ADD COLUMN starts_at              TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN ends_at                TIMESTAMP(6) WITH TIME ZONE,
    ADD CONSTRAINT chk_campaign_period CHECK (starts_at IS NULL OR ends_at IS NULL OR starts_at <= ends_at);

CREATE TABLE campaign_material (
    campaign_id  UUID          NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    position     INTEGER       NOT NULL,
    kind         VARCHAR(16)   NOT NULL CHECK (kind IN ('FILE', 'LINK')),
    title        VARCHAR(255)  NOT NULL,
    url          VARCHAR(2048),
    file_key     VARCHAR(512),
    content_type VARCHAR(255),
    size_bytes   BIGINT CHECK (size_bytes >= 0),
    PRIMARY KEY (campaign_id, position),
    CHECK ((kind = 'FILE' AND file_key IS NOT NULL) OR (kind = 'LINK' AND url IS NOT NULL))
);
