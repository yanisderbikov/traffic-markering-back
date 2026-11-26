CREATE TABLE partner (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    code       VARCHAR(16) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);

ALTER TABLE users ADD COLUMN referred_by BIGINT REFERENCES partner (id) ON DELETE SET NULL;
CREATE INDEX idx_users_referred_by ON users (referred_by) WHERE referred_by IS NOT NULL;

ALTER TABLE transfer
    ADD COLUMN commission_kopecks BIGINT NOT NULL DEFAULT 0 CHECK (commission_kopecks >= 0);

ALTER TABLE wallet_transaction DROP CONSTRAINT IF EXISTS wallet_transaction_type_check;
ALTER TABLE wallet_transaction
    ADD CONSTRAINT wallet_transaction_type_check
        CHECK (type IN ('TOP_UP', 'WITHDRAWAL', 'ALLOCATION', 'RELEASE', 'EARNING', 'PAYOUT', 'REFERRAL_REWARD'));

CREATE TABLE referral_reward (
    id                    BIGSERIAL PRIMARY KEY,
    partner_id            BIGINT NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    referral_id           BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_transaction_id BIGINT NOT NULL UNIQUE REFERENCES wallet_transaction (id) ON DELETE CASCADE,
    reward_transaction_id BIGINT NOT NULL UNIQUE REFERENCES wallet_transaction (id) ON DELETE CASCADE,
    commission_kopecks    BIGINT NOT NULL CHECK (commission_kopecks > 0),
    reward_kopecks        BIGINT NOT NULL CHECK (reward_kopecks > 0),
    created_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_referral_reward_partner ON referral_reward (partner_id, created_at DESC);
