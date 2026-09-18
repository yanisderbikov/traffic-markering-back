ALTER TABLE campaign
    ADD COLUMN view_region VARCHAR(32) NOT NULL DEFAULT 'WORLD' CHECK (view_region IN ('RUSSIA', 'CIS', 'WORLD'));

ALTER TABLE campaign
    ALTER COLUMN view_region DROP DEFAULT;

ALTER TABLE application
    ADD COLUMN country_views TEXT;

ALTER TABLE application_view_snapshot
    ADD COLUMN country_views TEXT;
