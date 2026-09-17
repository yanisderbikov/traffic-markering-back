package ru.trafficmarkering.service.geo;

import java.util.Map;

/**
 * Просмотры одного ролика с разбивкой по географии.
 *
 * {@code viewsByCountry} — код страны в формате ISO 3166-1 alpha-2 (RU, BY, KZ, US, ...)
 * в верхнем регистре → число просмотров из неё. Сумма по карте может быть меньше
 * {@code totalViews}: часть просмотров площадка может не геолоцировать — это штатная
 * ситуация, а не ошибка, и обрабатывать её должен код, который уже знает про регионы.
 */
public record VideoGeoViews(long totalViews, Map<String, Long> viewsByCountry) {
}
