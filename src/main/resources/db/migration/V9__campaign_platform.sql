CREATE TABLE campaign_platform (
    campaign_id UUID        NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    platform    VARCHAR(32) NOT NULL CHECK (platform IN ('INSTAGRAM', 'TIKTOK', 'YOUTUBE_SHORTS')),
    PRIMARY KEY (campaign_id, platform)
);

INSERT INTO campaign_platform (campaign_id, platform)
SELECT c.id, p.platform
FROM campaign c
         CROSS JOIN (VALUES ('INSTAGRAM'), ('TIKTOK'), ('YOUTUBE_SHORTS')) AS p (platform);
