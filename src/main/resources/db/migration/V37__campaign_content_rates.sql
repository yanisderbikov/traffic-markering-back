-- Ставки по тематикам роликов: заказчик сам задаёт тематики («CS и Dota 2», «Юмор») и ставку за каждую,
-- криатор при подаче выбирает тематику своего ролика — по её ставке ролик и считается.
CREATE TABLE campaign_content_rate
(
    campaign_id                UUID        NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    position                   INT         NOT NULL,
    name                       VARCHAR(64) NOT NULL,
    rate_per_thousand_kopecks  BIGINT      NOT NULL CHECK (rate_per_thousand_kopecks > 0),
    PRIMARY KEY (campaign_id, position)
);

-- Тематика, которую выбрал криатор при подаче; ставка по ней фиксируется в base_rate_kopecks
ALTER TABLE application
    ADD COLUMN content_topic VARCHAR(64);
