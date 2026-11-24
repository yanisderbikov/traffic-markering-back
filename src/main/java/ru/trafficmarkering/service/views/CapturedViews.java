package ru.trafficmarkering.service.views;

import ru.trafficmarkering.model.application.ApplicationViewSnapshot;

/** Свежий замер одного ролика, уже записанный в историю: по нему считается работа «по снимку». */
public record CapturedViews(ApplicationViewSnapshot snapshot) {
}
