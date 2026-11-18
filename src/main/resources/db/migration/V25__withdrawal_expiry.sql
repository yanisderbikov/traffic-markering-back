UPDATE transfer t
SET expires_at = wt.created_at + INTERVAL '1 hour'
FROM wallet_transaction wt
WHERE wt.id = t.transaction_id
  AND wt.type = 'WITHDRAWAL'
  AND wt.status = 'SENT';
