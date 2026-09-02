-- ============================================================================
--  V2 — демо-данные, чтобы на чистой базе доска объявлений не была пустой
--  и было чем залогиниться сразу после `docker compose up`.
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

-- Три активных объявления с разными ставками и бюджетами, чтобы карточки на доске
-- отличались друг от друга. spent_kopecks у всех ноль, и иначе быть не может:
-- это всегда сумма начислений по одобренным откликам, а откликов тут ещё нет
INSERT INTO campaign (id, public_id, customer_id, title, description,
                      rate_per_thousand_kopecks, budget_kopecks, spent_kopecks, status, created_at, updated_at)
VALUES (gen_random_uuid(), 'DEMOCMP1',
        (SELECT id FROM users WHERE username = 'demo-customer@traffic.ru'),
        'Обзор приложения для доставки еды',
        'Нужен вертикальный ролик 30–45 секунд: показываешь заказ в приложении от корзины до курьера у двери. Тон дружелюбный, без агрессивной рекламы. Ссылку и промокод пришлём в личке после одобрения отклика.',
        35000, 5000000, 0, 'ACTIVE', now(), now()),
       (gen_random_uuid(), 'DEMOCMP2',
        (SELECT id FROM users WHERE username = 'demo-customer@traffic.ru'),
        'Интеграция в ролик про утренние привычки',
        'Ищем криаторов с аудиторией 18–30 лет. Формат — нативное упоминание сервиса в середине ролика, 15–20 секунд. Обязательно живой опыт использования, а не зачитанный текст.',
        22000, 3000000, 0, 'ACTIVE', now(), now()),
       (gen_random_uuid(), 'DEMOCMP3',
        (SELECT id FROM users WHERE username = 'demo-customer@traffic.ru'),
        'Распаковка коробки мерча',
        'Пришлём коробку с мерчем, снимаешь распаковку и первые впечатления. Никакого сценария: чем честнее реакция, тем лучше. Площадки — любые вертикальные.',
        50000, 10000000, 0, 'ACTIVE', now(), now());
