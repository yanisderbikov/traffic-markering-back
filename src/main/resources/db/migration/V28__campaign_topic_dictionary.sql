-- Тематики переезжают из enum в справочник: если подходящей нет, заказчик добавляет свою.
-- code остаётся строковым ключом — campaign.topic хранит те же значения, что и раньше.
CREATE TABLE campaign_topic (
    code                              VARCHAR(32) PRIMARY KEY,
    name                              VARCHAR(64) NOT NULL,
    -- Нижний регистр, «ё» → «е», одиночные пробелы: по нему ищем и не даём завести дубль.
    -- Считает приложение, а не lower() — так не зависим от локали базы
    normalized_name                   VARCHAR(64) NOT NULL UNIQUE,
    -- Средняя рыночная ставка за 1000 просмотров; у добавленных заказчиками тематик её нет
    average_rate_per_thousand_kopecks BIGINT CHECK (average_rate_per_thousand_kopecks > 0),
    created_by                        BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at                        TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);

INSERT INTO campaign_topic (code, name, normalized_name, average_rate_per_thousand_kopecks)
VALUES ('ENTERTAINMENT', 'Развлечения и юмор', 'развлечения и юмор', 8000),
       ('GAMING', 'Игры', 'игры', 10000),
       ('LIFESTYLE', 'Лайфстайл', 'лайфстайл', 12000),
       ('FOOD', 'Еда и рестораны', 'еда и рестораны', 12000),
       ('BEAUTY_FASHION', 'Красота и мода', 'красота и мода', 15000),
       ('SPORT_HEALTH', 'Спорт и здоровье', 'спорт и здоровье', 15000),
       ('TRAVEL', 'Путешествия', 'путешествия', 15000),
       ('APPS', 'Приложения и сервисы', 'приложения и сервисы', 18000),
       ('TECH', 'Технологии и гаджеты', 'технологии и гаджеты', 20000),
       ('EDUCATION', 'Образование', 'образование', 20000),
       ('AUTO', 'Авто', 'авто', 22000),
       ('REAL_ESTATE', 'Недвижимость', 'недвижимость', 30000),
       ('FINANCE', 'Финансы и инвестиции', 'финансы и инвестиции', 35000),
       ('OTHER', 'Другое', 'другое', 15000);

ALTER TABLE campaign DROP CONSTRAINT IF EXISTS campaign_topic_check;
ALTER TABLE campaign ADD CONSTRAINT fk_campaign_topic FOREIGN KEY (topic) REFERENCES campaign_topic (code);

-- По нему считается популярность тематик
CREATE INDEX idx_campaign_topic ON campaign (topic);
