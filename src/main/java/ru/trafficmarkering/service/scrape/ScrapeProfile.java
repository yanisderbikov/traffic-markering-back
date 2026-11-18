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
        Privacy privacy
) {

    /**
     * Закрыт ли профиль. UNKNOWN — ответ без признака закрытости: свой ScrapeCreators не отдаёт is_private
     * Instagram, когда публичный embed профиля пуст (профиль закрыт, встраивание выключено или Instagram
     * не показал виджет). Настоящий ScrapeCreators признак отдаёт всегда.
     */
    public enum Privacy {
        PUBLIC, PRIVATE, UNKNOWN
    }
}
