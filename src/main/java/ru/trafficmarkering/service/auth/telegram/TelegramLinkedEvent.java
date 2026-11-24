package ru.trafficmarkering.service.auth.telegram;

/** Telegram привязан к учётке после входа на сайте по почте («У меня есть аккаунт») */
public record TelegramLinkedEvent(long telegramId, String name, String email) {
}
