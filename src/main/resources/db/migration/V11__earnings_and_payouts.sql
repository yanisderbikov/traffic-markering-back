ALTER TABLE application
    ADD COLUMN credited_kopecks BIGINT NOT NULL DEFAULT 0 CHECK (credited_kopecks >= 0);

DO $$
DECLARE
    type_check TEXT;
BEGIN
    SELECT conname
    INTO type_check
    FROM pg_constraint
    WHERE conrelid = 'wallet_transaction'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%type%';
    IF type_check IS NOT NULL THEN
        EXECUTE format('ALTER TABLE wallet_transaction DROP CONSTRAINT %I', type_check);
    END IF;
END $$;

ALTER TABLE wallet_transaction
    ADD CONSTRAINT wallet_transaction_type_check
        CHECK (type IN ('TOP_UP', 'WITHDRAWAL', 'ALLOCATION', 'RELEASE', 'EARNING', 'PAYOUT'));

ALTER TABLE wallet_transaction
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'DONE'
        CHECK (status IN ('DONE', 'PENDING', 'SENT', 'CONFIRMED', 'REJECTED', 'CANCELLED'));
CREATE INDEX idx_wallet_transaction_type_status ON wallet_transaction (type, status, created_at DESC);

CREATE TABLE payout_request (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  BIGINT      NOT NULL UNIQUE REFERENCES wallet_transaction (id) ON DELETE CASCADE,
    tron_address    VARCHAR(64) NOT NULL,
    finance_comment TEXT,
    reject_reason   TEXT,
    processed_by    BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    sent_at         TIMESTAMP(6) WITH TIME ZONE,
    confirmed_at    TIMESTAMP(6) WITH TIME ZONE,
    closed_at       TIMESTAMP(6) WITH TIME ZONE,
    created_at      TIMESTAMP(6) WITH TIME ZONE,
    updated_at      TIMESTAMP(6) WITH TIME ZONE
);

CREATE TABLE payout_proof (
    payout_request_id BIGINT       NOT NULL REFERENCES payout_request (id) ON DELETE CASCADE,
    position          INT          NOT NULL,
    file_key          VARCHAR(512) NOT NULL,
    PRIMARY KEY (payout_request_id, position)
);

INSERT INTO wallet (user_id, balance_kopecks, created_at, updated_at)
SELECT u.id, 0, now(), now()
FROM users u
WHERE u.role = 'CREATOR'
  AND NOT EXISTS (SELECT 1 FROM wallet w WHERE w.user_id = u.id);
