# Свой сервер Telegram Bot API в режиме --local: бот принимает и отправляет файлы до 2 ГБ, а не 20/50 МБ.
# Снаружи один порт (nginx): API проксируется на сервер, скачанные файлы отдаются по их локальному пути.
# Публичного домена у сервиса нет — ходит только бэкенд по приватной сети Railway.
FROM aiogram/telegram-bot-api:latest

RUN apk add --no-cache nginx

ENV TELEGRAM_WORK_DIR=/var/lib/telegram-bot-api \
    TELEGRAM_TEMP_DIR=/tmp/telegram-bot-api \
    TELEGRAM_LOCAL=1 \
    TELEGRAM_HTTP_IP_ADDRESS=127.0.0.1 \
    TELEGRAM_HTTP_PORT=8081 \
    PORT=8080

COPY nginx.conf /etc/nginx/nginx.conf
COPY start.sh /start.sh

EXPOSE 8080
ENTRYPOINT ["/bin/sh", "/start.sh"]
