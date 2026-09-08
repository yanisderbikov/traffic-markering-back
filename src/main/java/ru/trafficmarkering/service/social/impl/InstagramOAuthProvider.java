package ru.trafficmarkering.service.social.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.service.social.SocialAccountData;
import ru.trafficmarkering.service.social.SocialOAuthProvider;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
class InstagramOAuthProvider implements SocialOAuthProvider {

    private static final String NAME = "Instagram";
    private static final String AUTH_URL = "https://www.instagram.com/oauth/authorize";
    private static final String TOKEN_URL = "https://api.instagram.com/oauth/access_token";
    private static final String LONG_LIVED_URL = "https://graph.instagram.com/access_token";
    private static final String ME_URL = "https://graph.instagram.com/v21.0/me";
    private static final String SCOPE = "instagram_business_basic,instagram_business_manage_insights";

    private final SocialHttpClient httpClient;

    @Value("${social.instagram.client-id}")
    private String clientId;

    @Value("${social.instagram.client-secret}")
    private String clientSecret;

    @Override
    public String slug() {
        return "instagram";
    }

    @Override
    public Platform platform() {
        return Platform.INSTAGRAM;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    @Override
    public String authorizationUrl(String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("state", state)
                .encode()
                .toUriString();
    }

    @Override
    public SocialAccountData exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri);
        form.add("code", stripFragment(code));

        Map<String, Object> shortLived = httpClient.postForm(TOKEN_URL, form, NAME);
        String shortLivedToken = SocialJson.text(shortLived, "access_token");
        if (shortLivedToken == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, NAME + " не выдал токен доступа");
        }

        String longLivedUrl = UriComponentsBuilder.fromUriString(LONG_LIVED_URL)
                .queryParam("grant_type", "ig_exchange_token")
                .queryParam("client_secret", clientSecret)
                .queryParam("access_token", shortLivedToken)
                .encode()
                .toUriString();
        Map<String, Object> longLived = httpClient.getJson(longLivedUrl, null, NAME);
        String accessToken = SocialJson.text(longLived, "access_token");
        if (accessToken == null) {
            accessToken = shortLivedToken;
        }

        String meUrl = UriComponentsBuilder.fromUriString(ME_URL)
                .queryParam("fields", "user_id,username,account_type,followers_count")
                .queryParam("access_token", accessToken)
                .encode()
                .toUriString();
        Map<String, Object> me = httpClient.getJson(meUrl, null, NAME);

        String externalId = SocialJson.text(me, "user_id");
        if (externalId == null) {
            externalId = SocialJson.text(shortLived, "user_id");
        }
        if (externalId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, NAME + " не отдал идентификатор аккаунта");
        }

        Long expiresIn = SocialJson.number(longLived, "expires_in");
        String username = SocialJson.text(me, "username");

        return new SocialAccountData(
                externalId,
                username,
                username,
                null,
                SocialJson.number(me, "followers_count"),
                accessToken,
                null,
                expiresIn == null ? null : Instant.now().plusSeconds(expiresIn),
                SCOPE);
    }

    private String stripFragment(String code) {
        int hash = code.indexOf('#');
        return hash < 0 ? code : code.substring(0, hash);
    }
}
