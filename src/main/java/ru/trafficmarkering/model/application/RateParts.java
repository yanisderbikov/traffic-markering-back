package ru.trafficmarkering.model.application;

/**
 * Состав ставки «ссылка и промокод» за 1000 просмотров, в копейках: база платится за сам рекламный
 * ролик, надбавки — за ссылку в профиле и промокод в закрепе, каждая независимо от другой.
 */
public record RateParts(long base, long link, long promo) {

    public long rate(boolean withLink, boolean withPromo) {
        return base + (withLink ? link : 0L) + (withPromo ? promo : 0L);
    }

    public long full() {
        return base + link + promo;
    }
}
