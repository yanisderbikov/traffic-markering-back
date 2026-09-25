UPDATE transfer t
SET confirmed_at = COALESCE(t.confirmed_at, now()),
    closed_at    = COALESCE(t.closed_at, now())
FROM wallet_transaction wt
WHERE wt.id = t.transaction_id
  AND wt.type = 'TOP_UP'
  AND wt.status IN ('PENDING', 'SENT');

UPDATE wallet_transaction
SET status = 'CONFIRMED'
WHERE type = 'TOP_UP'
  AND status IN ('PENDING', 'SENT');
