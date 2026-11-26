-- Объявления «ссылка и промокод»: оплата по снимку просмотров при подаче ролика,
-- база ставки плюс две независимые надбавки — за рекламную ссылку в профиле и за промокод в закрепе.
ALTER TABLE campaign
    ADD COLUMN payment_model                    VARCHAR(32) NOT NULL DEFAULT 'CONTINUOUS',
    ADD COLUMN promo_url                        VARCHAR(1024),
    ADD COLUMN promo_code                       VARCHAR(64),
    ADD COLUMN promo_benefit                    TEXT,
    ADD COLUMN link_bonus_per_thousand_kopecks  BIGINT,
    ADD COLUMN promo_bonus_per_thousand_kopecks BIGINT,
    ADD COLUMN placement_retention_days         INT,
    ADD COLUMN max_reward_per_video_kopecks     BIGINT;

ALTER TABLE campaign
    ADD CONSTRAINT chk_campaign_payment_model CHECK (payment_model IN ('CONTINUOUS', 'SNAPSHOT')),
    ADD CONSTRAINT chk_campaign_link_bonus CHECK (link_bonus_per_thousand_kopecks IS NULL OR link_bonus_per_thousand_kopecks >= 0),
    ADD CONSTRAINT chk_campaign_promo_bonus CHECK (promo_bonus_per_thousand_kopecks IS NULL OR promo_bonus_per_thousand_kopecks >= 0),
    ADD CONSTRAINT chk_campaign_retention_days CHECK (placement_retention_days IS NULL OR placement_retention_days BETWEEN 1 AND 90),
    ADD CONSTRAINT chk_campaign_max_reward CHECK (max_reward_per_video_kopecks IS NULL OR max_reward_per_video_kopecks > 0);

-- Отклик по такому объявлению: ставки фиксируются, когда криатор берёт оффер в работу,
-- просмотры — снимком при подаче. Надбавки живут отдельно от ролика: отказ по одной не трогает базу.
ALTER TABLE application
    ADD COLUMN base_rate_kopecks          BIGINT,
    ADD COLUMN link_bonus_kopecks         BIGINT,
    ADD COLUMN promo_bonus_kopecks        BIGINT,
    ADD COLUMN billing_snapshot_id        BIGINT REFERENCES application_view_snapshot (id) ON DELETE SET NULL,
    ADD COLUMN billing_views              BIGINT,
    ADD COLUMN submitted_at               TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN placement_retain_until     TIMESTAMP(6) WITH TIME ZONE,

    ADD COLUMN link_state                 VARCHAR(32) NOT NULL DEFAULT 'NOT_SELECTED',
    ADD COLUMN link_proof_key             VARCHAR(512),
    ADD COLUMN link_proof_url             VARCHAR(1024),
    ADD COLUMN link_check_state           VARCHAR(32),
    ADD COLUMN link_check_detail          TEXT,
    ADD COLUMN link_checked_at            TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN link_note                  TEXT,
    ADD COLUMN link_decided_at            TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN link_decided_by            BIGINT REFERENCES users (id) ON DELETE SET NULL,

    ADD COLUMN promo_state                VARCHAR(32) NOT NULL DEFAULT 'NOT_SELECTED',
    ADD COLUMN promo_proof_key            VARCHAR(512),
    ADD COLUMN promo_proof_url            VARCHAR(1024),
    ADD COLUMN promo_check_state          VARCHAR(32),
    ADD COLUMN promo_check_detail         TEXT,
    ADD COLUMN promo_checked_at           TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN promo_note                 TEXT,
    ADD COLUMN promo_decided_at           TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN promo_decided_by           BIGINT REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE application
    ADD CONSTRAINT chk_application_link_state
        CHECK (link_state IN ('NOT_SELECTED', 'PENDING', 'VERIFIED', 'EARNED', 'REJECTED')),
    ADD CONSTRAINT chk_application_promo_state
        CHECK (promo_state IN ('NOT_SELECTED', 'PENDING', 'VERIFIED', 'EARNED', 'REJECTED'));

-- Задача конца срока размещения ищет подтверждённые надбавки с истёкшим сроком
CREATE INDEX idx_application_placement_retain_until ON application (placement_retain_until)
    WHERE placement_retain_until IS NOT NULL;
