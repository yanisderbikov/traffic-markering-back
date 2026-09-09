package ru.trafficmarkering.service.views;

import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.application.ViewSource;

import java.util.Collection;
import java.util.Map;

public interface ViewCountProvider {

    Platform platform();

    ViewSource source();

    boolean isConfigured();

    Map<String, Long> fetchViews(Long creatorId, Collection<String> videoUrls);
}
