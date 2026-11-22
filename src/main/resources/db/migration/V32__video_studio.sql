-- Видео-студия (перенос Offer Studio): монтаж коротких роликов на сайте и в Telegram-боте.
-- Всё привязано к учётке сайта; Telegram — второй вход в тот же кабинет после привязки.

CREATE TABLE studio_profile (
    user_id              BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    active_template_id   UUID,
    favorite_template_id UUID,
    -- Очередь случайных фонов: каждый аккаунт проходит коллекцию без повторов
    preset_queue         TEXT        NOT NULL DEFAULT '',
    telegram_id          BIGINT UNIQUE,
    telegram_username    VARCHAR(64),
    telegram_linked_at   TIMESTAMP(6) WITH TIME ZONE,
    link_code            VARCHAR(48) UNIQUE,
    link_code_expires_at TIMESTAMP(6) WITH TIME ZONE
);

CREATE TABLE studio_asset (
    id               UUID PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind             VARCHAR(16)  NOT NULL CHECK (kind IN ('BANNER', 'BACKGROUND')),
    -- Общий каталог: файл загрузил администратор, видят все
    shared           BOOLEAN      NOT NULL DEFAULT FALSE,
    name             VARCHAR(120) NOT NULL,
    storage_key      VARCHAR(512) NOT NULL,
    content_type     VARCHAR(128) NOT NULL,
    size_bytes       BIGINT       NOT NULL CHECK (size_bytes >= 0),
    video            BOOLEAN      NOT NULL,
    created_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_studio_asset_owner ON studio_asset (user_id, kind, created_at DESC);
CREATE INDEX idx_studio_asset_shared ON studio_asset (kind, created_at DESC) WHERE shared;

CREATE TABLE studio_template (
    id                  UUID PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                VARCHAR(80)  NOT NULL,
    settings            TEXT         NOT NULL,
    captions            TEXT,
    banner_asset_id     UUID REFERENCES studio_asset (id) ON DELETE SET NULL,
    background_asset_id UUID REFERENCES studio_asset (id) ON DELETE SET NULL,
    created_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_studio_template_owner ON studio_template (user_id, updated_at DESC);

ALTER TABLE studio_profile
    ADD CONSTRAINT fk_studio_profile_active FOREIGN KEY (active_template_id)
        REFERENCES studio_template (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_studio_profile_favorite FOREIGN KEY (favorite_template_id)
        REFERENCES studio_template (id) ON DELETE SET NULL;

CREATE TABLE studio_job (
    id                UUID PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    origin            VARCHAR(16)  NOT NULL CHECK (origin IN ('WEB', 'TELEGRAM')),
    status            VARCHAR(16)  NOT NULL
        CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED', 'CANCELLED', 'EXPIRED')),
    filename          VARCHAR(160) NOT NULL,
    settings          TEXT         NOT NULL,
    captions          TEXT,
    preset            VARCHAR(32),
    progress          INT          NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
    error             TEXT,
    source_key        VARCHAR(512),
    banner_key        VARCHAR(512),
    background_key    VARCHAR(512),
    result_key        VARCHAR(512),
    result_size       BIGINT,
    batch_id          UUID,
    retry_of          UUID,
    telegram_chat_id  BIGINT,
    telegram_reply_to BIGINT,
    inputs_until      TIMESTAMP(6) WITH TIME ZONE,
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    started_at        TIMESTAMP(6) WITH TIME ZONE,
    finished_at       TIMESTAMP(6) WITH TIME ZONE
);
CREATE INDEX idx_studio_job_owner ON studio_job (user_id, created_at DESC);
CREATE INDEX idx_studio_job_queue ON studio_job (status, created_at) WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX idx_studio_job_batch ON studio_job (batch_id) WHERE batch_id IS NOT NULL;

-- Видео из чата, для которого пользователь ещё не выбрал режим монтажа (живёт 30 минут)
CREATE TABLE studio_chat_draft (
    id         UUID PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    chat_id    BIGINT       NOT NULL,
    message_id BIGINT       NOT NULL,
    file_id    VARCHAR(256) NOT NULL,
    filename   VARCHAR(160) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_studio_chat_draft_created ON studio_chat_draft (created_at);
