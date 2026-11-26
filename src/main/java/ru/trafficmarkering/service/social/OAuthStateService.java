package ru.trafficmarkering.service.social;

import ru.trafficmarkering.model.application.Platform;

public interface OAuthStateService {

    String issue(Long userId, Platform platform);

    Long verify(String state, Platform platform);
}
