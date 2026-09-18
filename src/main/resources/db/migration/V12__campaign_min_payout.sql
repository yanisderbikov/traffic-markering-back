ALTER TABLE campaign
    ADD COLUMN min_payout_kopecks BIGINT NOT NULL DEFAULT 500000 CHECK (min_payout_kopecks > 0);

ALTER TABLE campaign
    ALTER COLUMN min_payout_kopecks DROP DEFAULT;
