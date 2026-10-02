package ru.trafficmarkering.service.social;

import ru.trafficmarkering.model.application.Platform;

import java.util.Optional;

public interface SocialOAuthProvider {

    String slug();

    Platform platform();

    boolean isConfigured();

    String authorizationUrl(String state, String redirectUri);

    SocialAccountData exchangeCode(String code, String redirectUri);

    default Optional<RefreshedToken> refresh(String accessToken, String refreshToken) {
        return Optional.empty();
    }
}
