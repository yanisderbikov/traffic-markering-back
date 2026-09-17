-- Регион, по которому считаются оплачиваемые просмотры оффера (TASK-6).
-- Существующие объявления получают WORLDWIDE — это сохраняет их сегодняшнее поведение
-- (платить за все просмотры), а не тихо обнуляет им начисления.
ALTER TABLE campaign
    ADD COLUMN region VARCHAR(32) NOT NULL DEFAULT 'WORLDWIDE'
        CHECK (region IN ('RUSSIA', 'CIS', 'WORLDWIDE'));

ALTER TABLE campaign
    ALTER COLUMN region DROP DEFAULT;
