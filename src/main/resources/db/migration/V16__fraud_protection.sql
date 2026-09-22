-- Антифрод: скоринг откликов, метрики роликов в снимках и репутация криаторов.

ALTER TABLE application
    ADD COLUMN video_published_at   TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN fraud_status         VARCHAR(32) NOT NULL DEFAULT 'CLEAN'
        CHECK (fraud_status IN ('CLEAN', 'SUSPICIOUS', 'FRAUD', 'VERIFIED')),
    ADD COLUMN fraud_score          INTEGER NOT NULL DEFAULT 0 CHECK (fraud_score >= 0),
    ADD COLUMN fraud_flags          TEXT,
    ADD COLUMN fraud_checked_at     TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN fraud_reviewed_at    TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN fraud_reviewed_by    BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN fraud_review_comment TEXT;

CREATE INDEX idx_application_fraud_status ON application (fraud_status);

-- Метрики ролика на момент замера: по ним считается скоринг. NULL — площадка не отдала.
ALTER TABLE application_view_snapshot
    ADD COLUMN likes               BIGINT CHECK (likes >= 0),
    ADD COLUMN comments            BIGINT CHECK (comments >= 0),
    ADD COLUMN shares              BIGINT CHECK (shares >= 0),
    ADD COLUMN saves               BIGINT CHECK (saves >= 0),
    ADD COLUMN reach               BIGINT CHECK (reach >= 0),
    ADD COLUMN engaged_views       BIGINT CHECK (engaged_views >= 0),
    ADD COLUMN avg_watch_seconds   DOUBLE PRECISION CHECK (avg_watch_seconds >= 0),
    ADD COLUMN avg_view_percentage DOUBLE PRECISION CHECK (avg_view_percentage >= 0),
    ADD COLUMN traffic_sources     TEXT;

-- Репутация криатора: уровень доверия и кто его выставил.
ALTER TABLE creator_profile
    ADD COLUMN trust_level      VARCHAR(16) NOT NULL DEFAULT 'NEW'
        CHECK (trust_level IN ('NEW', 'TRUSTED', 'RESTRICTED', 'BLOCKED')),
    ADD COLUMN trust_manual     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN trust_note       TEXT,
    ADD COLUMN trust_updated_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN trust_updated_by BIGINT REFERENCES users (id) ON DELETE SET NULL;

-- Криаторы, которым платформа уже заплатила хотя бы за три ролика, начинают проверенными:
-- ограничения новичков задним числом на них не распространяются.
UPDATE creator_profile
SET trust_level = 'TRUSTED'
WHERE user_id IN (SELECT creator_id
                  FROM application
                  WHERE credited_kopecks > 0
                  GROUP BY creator_id
                  HAVING COUNT(*) >= 3);
