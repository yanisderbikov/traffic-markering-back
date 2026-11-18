ALTER TABLE wallet_transaction ADD COLUMN public_id VARCHAR(16);

DO $$
DECLARE
    alphabet  CONSTANT TEXT := 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    row_id    BIGINT;
    candidate TEXT;
BEGIN
    FOR row_id IN SELECT id FROM wallet_transaction WHERE public_id IS NULL LOOP
        LOOP
            candidate := '';
            FOR i IN 1..8 LOOP
                candidate := candidate || substr(alphabet, 1 + floor(random() * 36)::INT, 1);
            END LOOP;
            EXIT WHEN NOT EXISTS (SELECT 1 FROM wallet_transaction WHERE public_id = candidate);
        END LOOP;
        UPDATE wallet_transaction SET public_id = candidate WHERE id = row_id;
    END LOOP;
END $$;

ALTER TABLE wallet_transaction ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE wallet_transaction ADD CONSTRAINT wallet_transaction_public_id_key UNIQUE (public_id);
