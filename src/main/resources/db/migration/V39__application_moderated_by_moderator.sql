-- Кто из менеджеров бота решил ролик: на каждом ролике видно, кто нажал «Принять» или «Отклонить».
-- moderated_by — пользователь сайта (админ или заказчик), moderated_by_moderator — менеджер из Telegram
ALTER TABLE application
    ADD COLUMN moderated_by_moderator BIGINT REFERENCES moderator (id) ON DELETE SET NULL;
