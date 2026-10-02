package ru.trafficmarkering.service.social;

import ru.trafficmarkering.model.application.Platform;

import java.util.Optional;

public interface SocialTokenService {

    Optional<String> accessToken(Long userId, Platform platform);
}
