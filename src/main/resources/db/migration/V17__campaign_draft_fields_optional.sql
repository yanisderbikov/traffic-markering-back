ALTER TABLE campaign
    ALTER COLUMN title DROP NOT NULL,
    ALTER COLUMN description DROP NOT NULL,
    ALTER COLUMN rate_per_thousand_kopecks DROP NOT NULL,
    ALTER COLUMN budget_kopecks DROP NOT NULL,
    ALTER COLUMN min_payout_kopecks DROP NOT NULL,
    ADD CONSTRAINT chk_campaign_filled_outside_draft CHECK (
        status = 'DRAFT'
        OR (title IS NOT NULL
            AND description IS NOT NULL
            AND rate_per_thousand_kopecks IS NOT NULL
            AND budget_kopecks IS NOT NULL
            AND min_payout_kopecks IS NOT NULL));
