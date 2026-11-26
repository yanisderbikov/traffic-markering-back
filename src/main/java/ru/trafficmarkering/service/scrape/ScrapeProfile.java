package ru.trafficmarkering.service.scrape;

import java.util.List;

/**
 * Публичный профиль на площадке. externalId — стабильный идентификатор площадки
 * (TikTok user id, Instagram pk, YouTube channelId): ник можно сменить, его — нет.
 * bio — описание профиля, в нём криатор оставляет проверочный код.
 * links — кликабельные ссылки профиля (TikTok bioLink, Instagram bio_links и external_url, ссылки канала
 * YouTube); null — источник таких полей не отдал, пустой список — отдал, но ссылок нет.
 */
public record ScrapeProfile(
        String externalId,
        String username,
        String displayName,
        String avatarUrl,
        Long followers,
        String bio,
        Privacy privacy,
        List<String> links
) {

    /** Для мест, где ссылки профиля не нужны: источник их не разбирал. */
    public ScrapeProfile(String externalId, String username, String displayName, String avatarUrl,
                         Long followers, String bio, Privacy privacy) {
        this(externalId, username, displayName, avatarUrl, followers, bio, privacy, null);
    }

    /** Отдаёт ли источник ссылки профиля: null — поля нет вовсе, и «нашей ссылки нет» отсюда не следует. */
    public boolean linksKnown() {
        return links != null;
    }

    /**
     * Закрыт ли профиль. UNKNOWN — ответ без признака закрытости: свой ScrapeCreators не отдаёт is_private
     * Instagram, когда публичный embed профиля пуст (профиль закрыт, встраивание выключено или Instagram
     * не показал виджет). Настоящий ScrapeCreators признак отдаёт всегда.
     */
    public enum Privacy {
        PUBLIC, PRIVATE, UNKNOWN
    }
}
