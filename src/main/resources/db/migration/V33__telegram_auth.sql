-- Вход и регистрация через Telegram-бота. Одна запись — одна попытка входа:
-- с сайта (браузер ждёт подтверждения в боте) или из бота (одноразовая ссылка на сайт).
-- Браузер держит секрет, в базе лежит только его SHA-256.

CREATE TABLE telegram_auth (
    id                VARCHAR(32) PRIMARY KEY,
    secret_hash       VARCHAR(64)  NOT NULL UNIQUE,
    origin            VARCHAR(8)   NOT NULL CHECK (origin IN ('SITE', 'BOT')),
    status            VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'DONE', 'CANCELLED')),
    -- Роль и приглашение для новой учётки, если вход начали со страницы регистрации
    role              VARCHAR(32),
    referral_code     VARCHAR(64),
    telegram_id       BIGINT,
    telegram_username VARCHAR(64),
    telegram_name     VARCHAR(255),
    user_id           BIGINT REFERENCES users (id) ON DELETE CASCADE,
    client_info       VARCHAR(255),
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_telegram_auth_expires ON telegram_auth (expires_at);
