package ru.trafficmarkering.service.views;

import ru.trafficmarkering.model.application.Platform;

import java.util.Optional;

/**
 * Источник просмотров ролика. Отделён интерфейсом, потому что у каждой площадки
 * счётчик достаётся по-своему: сегодня их проставляет внешний анализатор через
 * техническую ручку, завтра здесь может появиться реализация с походом в API площадки.
 */
public interface ViewCountProvider {

    /** Просмотры ролика по ссылке; Optional.empty() — площадка пока не поддерживается. */
    Optional<Long> fetchViews(Platform platform, String videoUrl);
}
