-- ============================================================================
--  V2 — демо-пользователи, чтобы было чем залогиниться сразу после `docker compose up`.
--
--  Логины: demo-customer@traffic.ru (заказчик) и demo-creator@traffic.ru (криатор).
--  Пароль у обоих открытым текстом: demo1234
--  Ниже лежит его настоящий bcrypt-хеш (cost 10), проверенный BCryptPasswordEncoder.
--
--  На проде демо-пользователей стоит удалить:
--    DELETE FROM users WHERE username LIKE 'demo-%@traffic.ru';
-- ============================================================================

INSERT INTO users (username, password, name, role, created_at)
VALUES ('demo-customer@traffic.ru', '$2a$10$luF7.N6Htj.A8a9Dn9PHw.NblWBj8MWsskprMEVJZ1tF2TCr23Lme', 'Демо Заказчик', 'CUSTOMER', now()),
       ('demo-creator@traffic.ru', '$2a$10$luF7.N6Htj.A8a9Dn9PHw.NblWBj8MWsskprMEVJZ1tF2TCr23Lme', 'Демо Криатор', 'CREATOR', now());

-- Профили заводятся сразу вместе с пользователем — так же, как это делает регистрация
INSERT INTO customer_profile (id, user_id, company, about, telegram, website, created_at, updated_at)
VALUES (gen_random_uuid(),
        (SELECT id FROM users WHERE username = 'demo-customer@traffic.ru'),
        'Демо Бренд',
        'Показательный заказчик: заводит объявления, чтобы доска не пустовала.',
        '@demo_brand',
        'https://example.ru',
        now(), now());

INSERT INTO creator_profile (id, user_id, display_name, bio, telegram, instagram, tiktok, youtube_shorts, created_at, updated_at)
VALUES (gen_random_uuid(),
        (SELECT id FROM users WHERE username = 'demo-creator@traffic.ru'),
        'demo creator',
        'Показательный криатор: снимает вертикальные ролики.',
        '@demo_creator',
        NULL, NULL, NULL,
        now(), now());
