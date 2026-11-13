package ru.trafficmarkering.service.scrape;

/**
 * Публичный профиль на площадке. externalId — стабильный идентификатор площадки
 * (TikTok user id, Instagram pk, YouTube channelId): ник можно сменить, его — нет.
 * bio — описание профиля, в нём криатор оставляет проверочный код.
 */
public record ScrapeProfile(
        String externalId,
        String username,
        String displayName,
        String avatarUrl,
        Long followers,
        String bio,
        boolean privateAccount
) {
}
