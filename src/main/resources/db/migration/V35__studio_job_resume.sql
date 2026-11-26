-- Сборка переживает перезапуск сервера: задача возвращается в очередь, прогресс правится в том же сообщении
ALTER TABLE studio_job ADD COLUMN attempts INT NOT NULL DEFAULT 0;
ALTER TABLE studio_job ADD COLUMN heartbeat_at TIMESTAMPTZ;
ALTER TABLE studio_job ADD COLUMN progress_message_id BIGINT;
