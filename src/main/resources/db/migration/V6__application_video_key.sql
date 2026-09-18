ALTER TABLE application
    ADD COLUMN video_key VARCHAR(512);

UPDATE application
SET video_key = platform || ':' || COALESCE(
        CASE
            WHEN platform = 'YOUTUBE_SHORTS' THEN COALESCE(
                    substring(video_url from '/(?:shorts|embed|live|v)/([A-Za-z0-9_-]{6,64})'),
                    substring(video_url from '[?&]v=([A-Za-z0-9_-]{6,64})'),
                    substring(video_url from 'youtu\.be/([A-Za-z0-9_-]{6,64})'))
            WHEN platform = 'TIKTOK' THEN substring(video_url from '/video/([0-9]{6,32})')
            END,
        regexp_replace(
                regexp_replace(
                        regexp_replace(lower(video_url), '^https?://(www\.)?', ''),
                        '[?#].*$', ''),
                '/+$', ''));

ALTER TABLE application
    ALTER COLUMN video_key SET NOT NULL;

CREATE UNIQUE INDEX uq_application_video_key
    ON application (video_key)
    WHERE status <> 'REJECTED';
