package ru.trafficmarkering.model.auth;

public enum TelegramAuthStatus {
    /** Ждём действия в боте */
    PENDING,
    /** Telegram подтвердил; если учётки нет — ждём входа по почте на сайте */
    CONFIRMED,
    /** Токен выдан или Telegram привязан — запись больше не работает */
    DONE,
    CANCELLED
}
