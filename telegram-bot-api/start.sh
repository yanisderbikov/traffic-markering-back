#!/bin/sh
set -e
mkdir -p "$TELEGRAM_WORK_DIR" "$TELEGRAM_TEMP_DIR"

# Скачанные файлы бэкенд забирает сразу; старше часа — удаляем, чтобы диск не рос
(
  while true; do
    sleep 600
    find "$TELEGRAM_WORK_DIR" -mindepth 3 -type f -mmin +60 -delete 2>/dev/null || true
    find "$TELEGRAM_TEMP_DIR" -type f -mmin +60 -delete 2>/dev/null || true
  done
) &

nginx
exec /docker-entrypoint.sh
