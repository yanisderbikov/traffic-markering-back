-- Рассылки из админки: в Telegram (кто привязал бота) и/или на почту. Отправка — тасками NOTIFICATION
CREATE TABLE broadcast (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    audience    VARCHAR(16)  NOT NULL,
    channel     VARCHAR(16)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    text        TEXT         NOT NULL,
    button_text VARCHAR(64),
    button_url  VARCHAR(512),
    status      VARCHAR(16)  NOT NULL,
    total       INT          NOT NULL DEFAULT 0,
    sent        INT          NOT NULL DEFAULT 0,
    failed      INT          NOT NULL DEFAULT 0,
    created_by  BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    finished_at TIMESTAMP(6) WITH TIME ZONE
);
CREATE INDEX idx_broadcast_created ON broadcast (created_at DESC);
