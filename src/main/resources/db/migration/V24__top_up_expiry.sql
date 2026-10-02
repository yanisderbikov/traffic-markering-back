ALTER TABLE wallet_transaction DROP CONSTRAINT IF EXISTS wallet_transaction_status_check;

ALTER TABLE wallet_transaction
    ADD CONSTRAINT wallet_transaction_status_check
        CHECK (status IN ('DONE', 'PENDING', 'SENT', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'EXPIRED'));

ALTER TABLE transfer ADD COLUMN expires_at TIMESTAMP(6) WITH TIME ZONE;

UPDATE transfer t
SET expires_at = wt.created_at + INTERVAL '1 hour'
FROM wallet_transaction wt
WHERE wt.id = t.transaction_id
  AND wt.type = 'TOP_UP'
  AND wt.status = 'PENDING';

CREATE INDEX idx_transfer_expires_at ON transfer (expires_at) WHERE expires_at IS NOT NULL;
