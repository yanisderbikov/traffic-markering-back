package ru.trafficmarkering.model.social;

/**
 * Как криатор доказал, что аккаунт его. От способа зависит, откуда берутся просмотры:
 * OAUTH — токен площадки и её официальный API, SCRAPE — код в описании профиля
 * и публичные данные через ScrapeCreators.
 */
public enum SocialLinkMethod {
    OAUTH,
    SCRAPE
}
