-- Менеджеры модерации в Telegram: супер-админ задаёт список ников, менеджер заходит в бота модерации
-- и сам настраивает часы приёма. Ролики на модерации раздаются им по одному.
CREATE TABLE moderator
(
    id            BIGSERIAL PRIMARY KEY,
    -- ник Telegram без @ в нижнем регистре: по нему супер-админ добавляет менеджера
    username      VARCHAR(64)              NOT NULL UNIQUE,
    -- false — убран из списка; строку не удаляем, по ней история решений
    enabled       BOOLEAN                  NOT NULL DEFAULT TRUE,
    telegram_id   BIGINT UNIQUE,
    chat_id       BIGINT,
    display_name  VARCHAR(255),
    -- смещение от UTC вида +03:00; часы приёма — в этом поясе
    utc_offset    VARCHAR(8),
    work_from     INT CHECK (work_from BETWEEN 0 AND 23),
    work_to       INT CHECK (work_to BETWEEN 0 AND 23),
    paused        BOOLEAN                  NOT NULL DEFAULT FALSE,
    -- сколько заявок подряд истекло без ответа; на пороге менеджер встаёт на паузу
    missed_in_row INT                      NOT NULL DEFAULT 0,
    last_seen_at  TIMESTAMP(6) WITH TIME ZONE,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Заявка у менеджера: кто, когда получил, что решил. Это же журнал для выборочных перепроверок и оплаты
CREATE TABLE moderation_assignment
(
    id                    BIGSERIAL PRIMARY KEY,
    application_id        UUID                     REFERENCES application (id) ON DELETE SET NULL,
    application_public_id VARCHAR(16)              NOT NULL,
    moderator_id          BIGINT                   NOT NULL REFERENCES moderator (id),
    -- ACTIVE, DECIDED, EXPIRED, CANCELLED, FAILED
    status                VARCHAR(16)              NOT NULL,
    -- APPROVED или REJECTED
    decision              VARCHAR(16),
    reason                TEXT,
    awaiting_reason       BOOLEAN                  NOT NULL DEFAULT FALSE,
    chat_id               BIGINT,
    message_id            BIGINT,
    -- true — сообщение с видео (правится подпись), false — текст со ссылкой
    with_video            BOOLEAN,
    -- file_id ролика в Telegram: следующему менеджеру видео уходит без повторного скачивания
    video_file_id         VARCHAR(256),
    assigned_at           TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    closed_at             TIMESTAMP(6) WITH TIME ZONE
);

-- Одна заявка — у одного менеджера, у менеджера — одна заявка
CREATE UNIQUE INDEX ux_moderation_assignment_active_application
    ON moderation_assignment (application_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX ux_moderation_assignment_active_moderator
    ON moderation_assignment (moderator_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_moderation_assignment_status_expires ON moderation_assignment (status, expires_at);
CREATE INDEX idx_moderation_assignment_moderator_closed ON moderation_assignment (moderator_id, closed_at);
CREATE INDEX idx_moderation_assignment_application ON moderation_assignment (application_id);
