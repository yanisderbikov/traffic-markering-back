package ru.trafficmarkering.service.social;

import ru.trafficmarkering.model.application.Platform;

public interface SocialOAuthProvider {

    String slug();

    Platform platform();

    boolean isConfigured();

    String authorizationUrl(String state, String redirectUri);

    SocialAccountData exchangeCode(String code, String redirectUri);
}
