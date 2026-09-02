-- ============================================================================
--  V1 — исходная схема traffic markering.
--  Заказчик (users.role = CUSTOMER) публикует объявление (campaign) со ставкой
--  за 1000 просмотров и выделенным бюджетом; криатор (users.role = CREATOR)
--  берёт его в работу откликом (application) со ссылкой на снятый ролик.
--  По мере набора просмотров бюджет «съедается»: campaign.spent_kopecks —
--  сумма начислений по всем одобренным откликам объявления.
--  Все деньги хранятся в КОПЕЙКАХ (BIGINT), чтобы не связываться с плавающей точкой.
-- ============================================================================

CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(255) NOT NULL UNIQUE,       -- e-mail, он же логин
    password   VARCHAR(255) NOT NULL,              -- bcrypt
    name       VARCHAR(255) NOT NULL,
    role       VARCHAR(32)  NOT NULL CHECK (role IN ('CUSTOMER', 'CREATOR', 'ADMIN')),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Профиль криатора: как с ним связаться и где он публикуется.
-- Ссылки на соцсети видит заказчик, поэтому они лежат отдельными колонками.
CREATE TABLE creator_profile (
    id             UUID PRIMARY KEY,
    user_id        BIGINT NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    display_name   VARCHAR(255),
    bio            TEXT,
    telegram       VARCHAR(255),
    instagram      VARCHAR(255),
    tiktok         VARCHAR(255),
    youtube_shorts VARCHAR(255),
    created_at     TIMESTAMP(6) WITH TIME ZONE,
    updated_at     TIMESTAMP(6) WITH TIME ZONE
);

-- Профиль заказчика: чьё объявление видит криатор на доске.
CREATE TABLE customer_profile (
    id         UUID PRIMARY KEY,
    user_id    BIGINT NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    company    VARCHAR(255),
    about      TEXT,
    telegram   VARCHAR(255),
    website    VARCHAR(255),
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE
);

-- Объявление на рекламную интеграцию.
-- public_id — короткий человекочитаемый номер для публичных ссылок:
-- внутренний UUID в адресную строку не отдаём.
CREATE TABLE campaign (
    id                        UUID PRIMARY KEY,
    public_id                 VARCHAR(16)  NOT NULL UNIQUE,
    customer_id               BIGINT       NOT NULL REFERENCES users (id),
    title                     VARCHAR(255) NOT NULL,
    description               TEXT         NOT NULL,
    rate_per_thousand_kopecks BIGINT       NOT NULL CHECK (rate_per_thousand_kopecks > 0),
    budget_kopecks            BIGINT       NOT NULL CHECK (budget_kopecks >= 0),
    spent_kopecks             BIGINT       NOT NULL DEFAULT 0 CHECK (spent_kopecks >= 0),
    status                    VARCHAR(32)  NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'COMPLETED')),
    created_at                TIMESTAMP(6) WITH TIME ZONE,
    updated_at                TIMESTAMP(6) WITH TIME ZONE
);
CREATE INDEX idx_campaign_customer_id ON campaign (customer_id);
CREATE INDEX idx_campaign_status ON campaign (status);

-- Отклик криатора на объявление: одна площадка, один ролик.
-- accrued_kopecks — сколько уже начислено за набранные просмотры;
-- значение пересчитывается целиком по объявлению, вручную его не правят.
CREATE TABLE application (
    id              UUID PRIMARY KEY,
    public_id       VARCHAR(16)   NOT NULL UNIQUE,
    campaign_id     UUID          NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    creator_id      BIGINT        NOT NULL REFERENCES users (id),
    platform        VARCHAR(32)   NOT NULL CHECK (platform IN ('TELEGRAM', 'INSTAGRAM', 'TIKTOK', 'YOUTUBE_SHORTS')),
    video_url       VARCHAR(1024) NOT NULL,
    comment         TEXT,
    status          VARCHAR(32)   NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED')),
    views           BIGINT        NOT NULL DEFAULT 0 CHECK (views >= 0),
    accrued_kopecks BIGINT        NOT NULL DEFAULT 0 CHECK (accrued_kopecks >= 0),
    views_synced_at TIMESTAMP(6) WITH TIME ZONE,
    created_at      TIMESTAMP(6) WITH TIME ZONE,
    updated_at      TIMESTAMP(6) WITH TIME ZONE,
    -- Один криатор — один отклик на объявление: повторные заявки не нужны
    CONSTRAINT uq_application_campaign_creator UNIQUE (campaign_id, creator_id)
);
CREATE INDEX idx_application_campaign_id ON application (campaign_id);
CREATE INDEX idx_application_creator_id ON application (creator_id);
