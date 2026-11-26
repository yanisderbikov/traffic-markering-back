-- «Только РФ» убран: Instagram и TikTok в РФ не работают, гарантировать такой регион нельзя.
-- Объявления с ним переводим на СНГ.
UPDATE campaign
SET view_region = 'CIS'
WHERE view_region = 'RUSSIA';

ALTER TABLE campaign
    DROP CONSTRAINT campaign_view_region_check;

ALTER TABLE campaign
    ADD CONSTRAINT campaign_view_region_check CHECK (view_region IN ('CIS', 'WORLD'));
