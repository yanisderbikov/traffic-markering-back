ALTER TABLE payout_request RENAME TO transfer;
ALTER SEQUENCE payout_request_id_seq RENAME TO transfer_id_seq;
ALTER TABLE transfer ALTER COLUMN tron_address DROP NOT NULL;
ALTER TABLE transfer ADD COLUMN tx_id VARCHAR(255);

ALTER TABLE payout_proof RENAME TO transfer_proof;
ALTER TABLE transfer_proof RENAME COLUMN payout_request_id TO transfer_id;
