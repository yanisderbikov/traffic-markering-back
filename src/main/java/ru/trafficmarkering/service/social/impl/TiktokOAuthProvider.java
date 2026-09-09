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
import ru.trafficmarkering.service.http.JsonHttpClient;
import ru.trafficmarkering.service.http.JsonNode;
import ru.trafficmarkering.service.social.RefreshedToken;
import ru.trafficmarkering.service.social.SocialAccountData;
import ru.trafficmarkering.service.social.SocialOAuthProvider;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class TiktokOAuthProvider implements SocialOAuthProvider {

    private static final String NAME = "TikTok";
    private static final String AUTH_URL = "https://www.tiktok.com/v2/auth/authorize/";
    private static final String TOKEN_URL = "https://open.tiktokapis.com/v2/oauth/token/";
    private static final String USER_URL = "https://open.tiktokapis.com/v2/user/info/"
            + "?fields=open_id,union_id,avatar_url,display_name,follower_count";
    private static final String SCOPE = "user.info.basic,user.info.profile,user.info.stats,video.list";

    private final JsonHttpClient httpClient;

    @Value("${social.tiktok.client-key}")
    private String clientKey;

    @Value("${social.tiktok.client-secret}")
    private String clientSecret;

    @Override
    public String slug() {
        return "tiktok";
    }

    @Override
    public Platform platform() {
        return Platform.TIKTOK;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(clientKey) && StringUtils.hasText(clientSecret);
    }

    @Override
    public String authorizationUrl(String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("client_key", clientKey)
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
        form.add("client_key", clientKey);
        form.add("client_secret", clientSecret);
        form.add("code", URLDecoder.decode(code, StandardCharsets.UTF_8));
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri);

        Map<String, Object> token = httpClient.postForm(TOKEN_URL, form, NAME);
        String accessToken = JsonNode.text(token, "access_token");
        if (accessToken == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, NAME + " не выдал токен доступа");
        }

        Map<String, Object> response = httpClient.getJson(USER_URL, accessToken, NAME);
        Map<String, Object> user = JsonNode.object(JsonNode.object(response, "data"), "user");
        String openId = JsonNode.text(user, "open_id");
        if (openId == null) {
            openId = JsonNode.text(token, "open_id");
        }
        if (openId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, NAME + " не отдал идентификатор аккаунта");
        }

        Long expiresIn = JsonNode.number(token, "expires_in");
        String displayName = JsonNode.text(user, "display_name");

        return new SocialAccountData(
                openId,
                displayName,
                displayName,
                JsonNode.text(user, "avatar_url"),
                JsonNode.number(user, "follower_count"),
                accessToken,
                JsonNode.text(token, "refresh_token"),
                expiresIn == null ? null : Instant.now().plusSeconds(expiresIn),
                JsonNode.text(token, "scope"));
    }

    @Override
    public Optional<RefreshedToken> refresh(String accessToken, String refreshToken) {
        if (!StringUtils.hasText(refreshToken) || !isConfigured()) {
            return Optional.empty();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_key", clientKey);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);

        Map<String, Object> token = httpClient.postForm(TOKEN_URL, form, NAME);
        String fresh = JsonNode.text(token, "access_token");
        if (fresh == null) {
            return Optional.empty();
        }
        Long expiresIn = JsonNode.number(token, "expires_in");
        String rotated = JsonNode.text(token, "refresh_token");
        return Optional.of(new RefreshedToken(
                fresh,
                rotated != null ? rotated : refreshToken,
                expiresIn == null ? null : Instant.now().plusSeconds(expiresIn)));
    }
}
