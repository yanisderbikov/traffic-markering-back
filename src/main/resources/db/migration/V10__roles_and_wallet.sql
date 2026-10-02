DO $$
DECLARE
    role_check TEXT;
BEGIN
    SELECT conname
    INTO role_check
    FROM pg_constraint
    WHERE conrelid = 'users'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%role%';
    IF role_check IS NOT NULL THEN
        EXECUTE format('ALTER TABLE users DROP CONSTRAINT %I', role_check);
    END IF;
END $$;

ALTER TABLE users
    ADD CONSTRAINT users_role_check
        CHECK (role IN ('CUSTOMER', 'CREATOR', 'FINANCE_MANAGER', 'ADMIN', 'SUPER_ADMIN'));

CREATE TABLE wallet (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    balance_kopecks BIGINT NOT NULL DEFAULT 0 CHECK (balance_kopecks >= 0),
    created_at      TIMESTAMP(6) WITH TIME ZONE,
    updated_at      TIMESTAMP(6) WITH TIME ZONE
);

CREATE TABLE wallet_transaction (
    id                    BIGSERIAL PRIMARY KEY,
    wallet_id             BIGINT      NOT NULL REFERENCES wallet (id) ON DELETE CASCADE,
    type                  VARCHAR(32) NOT NULL CHECK (type IN ('TOP_UP', 'WITHDRAWAL', 'ALLOCATION', 'RELEASE')),
    amount_kopecks        BIGINT      NOT NULL CHECK (amount_kopecks <> 0),
    balance_after_kopecks BIGINT      NOT NULL CHECK (balance_after_kopecks >= 0),
    campaign_id           UUID        REFERENCES campaign (id) ON DELETE SET NULL,
    actor_id              BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    comment               TEXT,
    created_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_wallet_transaction_wallet ON wallet_transaction (wallet_id, created_at DESC);

INSERT INTO wallet (user_id, balance_kopecks, created_at, updated_at)
SELECT id, 0, now(), now()
FROM users
WHERE role IN ('CUSTOMER', 'ADMIN');

INSERT INTO wallet (user_id, balance_kopecks, created_at, updated_at)
SELECT DISTINCT c.customer_id, 0, now(), now()
FROM campaign c
WHERE NOT EXISTS (SELECT 1 FROM wallet w WHERE w.user_id = c.customer_id);

INSERT INTO wallet_transaction (wallet_id, type, amount_kopecks, balance_after_kopecks, campaign_id, comment, created_at)
SELECT w.id, 'TOP_UP', c.budget_kopecks, c.budget_kopecks, NULL,
       'Бюджет объявления «' || c.title || '», выделенный до появления кошелька',
       COALESCE(c.created_at, now())
FROM campaign c
         JOIN wallet w ON w.user_id = c.customer_id
WHERE c.budget_kopecks > 0;

INSERT INTO wallet_transaction (wallet_id, type, amount_kopecks, balance_after_kopecks, campaign_id, comment, created_at)
SELECT w.id, 'ALLOCATION', -c.budget_kopecks, 0, c.id, c.title, COALESCE(c.created_at, now())
FROM campaign c
         JOIN wallet w ON w.user_id = c.customer_id
WHERE c.budget_kopecks > 0;
