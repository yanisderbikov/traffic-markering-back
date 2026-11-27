-- Заказчик выбирает несколько тематик из справочника и ставит ставку за каждую: строка ставки помнит код
-- тематики. У прежних ставок с названием от руки кода нет.
ALTER TABLE campaign_content_rate
    ADD COLUMN topic_code VARCHAR(32) REFERENCES campaign_topic (code);
