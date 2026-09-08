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
class YoutubeOAuthProvider implements SocialOAuthProvider {

    private static final String NAME = "YouTube";
    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String CHANNEL_URL =
            "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&mine=true";
    private static final String SCOPE = "https://www.googleapis.com/auth/youtube.readonly";

    private final SocialHttpClient httpClient;

    @Value("${social.youtube.client-id}")
    private String clientId;

    @Value("${social.youtube.client-secret}")
    private String clientSecret;

    @Override
    public String slug() {
        return "youtube";
    }

    @Override
    public Platform platform() {
        return Platform.YOUTUBE_SHORTS;
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
                .queryParam("access_type", "offline")
                .queryParam("prompt", "select_account consent")
                .queryParam("include_granted_scopes", "true")
                .queryParam("state", state)
                .encode()
                .toUriString();
    }

    @Override
    public SocialAccountData exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        Map<String, Object> token = httpClient.postForm(TOKEN_URL, form, NAME);
        String accessToken = SocialJson.text(token, "access_token");
        if (accessToken == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, NAME + " не выдал токен доступа");
        }

        Map<String, Object> channels = httpClient.getJson(CHANNEL_URL, accessToken, NAME);
        Map<String, Object> channel = SocialJson.firstObject(SocialJson.array(channels, "items"));
        String channelId = SocialJson.text(channel, "id");
        if (channelId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "У этого аккаунта Google нет канала YouTube — создайте канал и повторите привязку");
        }

        Map<String, Object> snippet = SocialJson.object(channel, "snippet");
        Map<String, Object> statistics = SocialJson.object(channel, "statistics");
        Map<String, Object> thumbnail = SocialJson.object(SocialJson.object(snippet, "thumbnails"), "default");
        Long expiresIn = SocialJson.number(token, "expires_in");

        return new SocialAccountData(
                channelId,
                SocialJson.text(snippet, "customUrl"),
                SocialJson.text(snippet, "title"),
                SocialJson.text(thumbnail, "url"),
                SocialJson.number(statistics, "subscriberCount"),
                accessToken,
                SocialJson.text(token, "refresh_token"),
                expiresIn == null ? null : Instant.now().plusSeconds(expiresIn),
                SocialJson.text(token, "scope"));
    }
}
