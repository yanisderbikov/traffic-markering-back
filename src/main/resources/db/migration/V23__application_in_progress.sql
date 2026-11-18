ALTER TABLE application ALTER COLUMN platform DROP NOT NULL;
ALTER TABLE application ALTER COLUMN video_url DROP NOT NULL;
ALTER TABLE application ALTER COLUMN video_key DROP NOT NULL;

ALTER TABLE application DROP CONSTRAINT IF EXISTS application_status_check;
ALTER TABLE application
    ADD CONSTRAINT application_status_check
        CHECK (status IN ('IN_PROGRESS', 'PENDING', 'APPROVED', 'REJECTED', 'COMPLETED'));

ALTER TABLE application
    ADD CONSTRAINT application_video_required
        CHECK (status = 'IN_PROGRESS' OR (platform IS NOT NULL AND video_url IS NOT NULL AND video_key IS NOT NULL));
