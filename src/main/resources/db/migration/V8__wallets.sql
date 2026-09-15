-- Wallets are deliberately separated from campaign accruals.
-- This migration only introduces balances and the immutable audit ledger;
-- order/campaign money movement is not wired here.

CREATE TABLE wallet (
    id               UUID PRIMARY KEY,
    user_id          BIGINT NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    balance_kopecks  BIGINT NOT NULL DEFAULT 0 CHECK (balance_kopecks >= 0),
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE wallet_transaction (
    id                     UUID PRIMARY KEY,
    wallet_id              UUID NOT NULL REFERENCES wallet (id) ON DELETE CASCADE,
    actor_user_id          BIGINT REFERENCES users (id) ON DELETE SET NULL,
    type                   VARCHAR(32) NOT NULL CHECK (type IN ('ADMIN_ADJUSTMENT')),
    amount_kopecks         BIGINT NOT NULL CHECK (amount_kopecks <> 0),
    balance_after_kopecks  BIGINT NOT NULL CHECK (balance_after_kopecks >= 0),
    reason                 VARCHAR(500) NOT NULL CHECK (length(trim(reason)) > 0),
    created_at             TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_wallet_transaction_wallet_created
    ON wallet_transaction (wallet_id, created_at DESC);
CREATE INDEX idx_wallet_transaction_actor
    ON wallet_transaction (actor_user_id);
