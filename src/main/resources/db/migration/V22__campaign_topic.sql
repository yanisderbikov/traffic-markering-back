ALTER TABLE campaign
    ADD COLUMN topic VARCHAR(32) CHECK (topic IN ('ENTERTAINMENT', 'GAMING', 'LIFESTYLE', 'FOOD', 'BEAUTY_FASHION',
                                                  'SPORT_HEALTH', 'TRAVEL', 'APPS', 'TECH', 'EDUCATION', 'AUTO',
                                                  'REAL_ESTATE', 'FINANCE', 'OTHER'));

-- Тематика обязательна для запуска: уже запущенным ставим «Другое», чтобы их можно было править.
UPDATE campaign
SET topic = 'OTHER'
WHERE status <> 'DRAFT';
