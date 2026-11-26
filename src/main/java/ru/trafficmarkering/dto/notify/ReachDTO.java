package ru.trafficmarkering.dto.notify;

/**
 * Охват по роли: всего учёток, привязали Telegram (всего и за 7 дней), есть почта,
 * и сколько получат рассылку «Telegram, иначе почта».
 */
public record ReachDTO(String role, long total, long telegram, long telegramWeek, long email, long reachable) {
}
