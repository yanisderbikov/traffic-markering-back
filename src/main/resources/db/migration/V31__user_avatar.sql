-- Аватарка пользователя: ключ файла в хранилище, сама картинка отдаётся по presigned-ссылке.
ALTER TABLE users ADD COLUMN avatar_key VARCHAR(255);
