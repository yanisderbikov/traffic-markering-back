-- Способ привязки аккаунта: OAUTH (токен площадки) или SCRAPE (код в описании профиля).
-- Существующие привязки — OAuth. Под флагом SCRAPE они не учитываются: криаторы
-- привязывают аккаунты заново, а при откате флага старые привязки снова в силе.
ALTER TABLE social_account
    ADD COLUMN link_method VARCHAR(16) NOT NULL DEFAULT 'OAUTH'
        CHECK (link_method IN ('OAUTH', 'SCRAPE'));

-- У привязки через ScrapeCreators токена нет.
ALTER TABLE social_account ALTER COLUMN access_token DROP NOT NULL;

-- Один и тот же канал может быть привязан и через OAuth, и по коду — это разные миры флага.
ALTER TABLE social_account DROP CONSTRAINT uq_social_account_platform_external;
ALTER TABLE social_account
    ADD CONSTRAINT uq_social_account_platform_method_external UNIQUE (platform, link_method, external_id);

CREATE INDEX idx_social_account_user_platform_method ON social_account (user_id, platform, link_method);
