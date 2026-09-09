package ru.trafficmarkering.service.views.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.application.ViewSource;
import ru.trafficmarkering.service.http.JsonHttpClient;
import ru.trafficmarkering.service.http.JsonNode;
import ru.trafficmarkering.service.social.SocialTokenService;
import ru.trafficmarkering.service.views.ViewCountProvider;
import ru.trafficmarkering.util.VideoUrls;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Log4j2
class InstagramViewCountProvider implements ViewCountProvider {

    private static final String NAME = "Instagram";
    private static final String MEDIA_URL = "https://graph.instagram.com/v21.0/me/media";
    private static final String INSIGHTS_URL = "https://graph.instagram.com/v21.0/%s/insights";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 5;

    private final JsonHttpClient httpClient;
    private final SocialTokenService socialTokenService;

    @Override
    public Platform platform() {
        return Platform.INSTAGRAM;
    }

    @Override
    public ViewSource source() {
        return ViewSource.INSTAGRAM_API;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public Map<String, Long> fetchViews(Long creatorId, Collection<String> videoUrls) {
        Optional<String> token = socialTokenService.accessToken(creatorId, Platform.INSTAGRAM);
        if (token.isEmpty()) {
            log.debug("У криатора {} нет живого токена Instagram, пропускаем {} роликов",
                    creatorId, videoUrls.size());
            return Map.of();
        }

        Map<String, String> wanted = new LinkedHashMap<>();
        for (String url : videoUrls) {
            String canonical = VideoUrls.canonical(url);
            if (canonical == null) {
                log.warn("Не разобрали ссылку Instagram, пропускаем: {}", url);
                continue;
            }
            wanted.put(canonical, url);
        }
        if (wanted.isEmpty()) {
            return Map.of();
        }

        Map<String, String> mediaIdByUrl = resolveMediaIds(token.get(), wanted);
        Map<String, Long> result = new LinkedHashMap<>();
        mediaIdByUrl.forEach((url, mediaId) -> {
            Long views = fetchMediaViews(token.get(), mediaId);
            if (views != null) {
                result.put(url, views);
            }
        });
        return result;
    }

    private Map<String, String> resolveMediaIds(String token, Map<String, String> wanted) {
        Map<String, String> found = new HashMap<>();
        String url = UriComponentsBuilder.fromUriString(MEDIA_URL)
                .queryParam("fields", "id,permalink")
                .queryParam("limit", PAGE_SIZE)
                .queryParam("access_token", token)
                .encode()
                .toUriString();

        for (int page = 0; page < MAX_PAGES && url != null && found.size() < wanted.size(); page++) {
            Map<String, Object> response;
            try {
                response = httpClient.getJson(url, null, NAME);
            } catch (Exception e) {
                log.warn("Не удалось получить список медиа Instagram: {}", e.getMessage());
                break;
            }
            for (Object item : JsonNode.array(response, "data")) {
                if (!(item instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> media = (Map<String, Object>) item;
                String canonical = VideoUrls.canonical(JsonNode.text(media, "permalink"));
                String mediaId = JsonNode.text(media, "id");
                String originalUrl = canonical == null ? null : wanted.get(canonical);
                if (originalUrl != null && mediaId != null) {
                    found.put(originalUrl, mediaId);
                }
            }
            url = JsonNode.text(JsonNode.object(response, "paging"), "next");
        }
        return found;
    }

    private Long fetchMediaViews(String token, String mediaId) {
        String url = UriComponentsBuilder.fromUriString(String.format(INSIGHTS_URL, mediaId))
                .queryParam("metric", "views")
                .queryParam("access_token", token)
                .encode()
                .toUriString();
        try {
            Map<String, Object> response = httpClient.getJson(url, null, NAME);
            Map<String, Object> metric = JsonNode.firstObject(JsonNode.array(response, "data"));
            Map<String, Object> value = JsonNode.firstObject(JsonNode.array(metric, "values"));
            return JsonNode.number(value, "value");
        } catch (Exception e) {
            log.warn("Не удалось получить просмотры медиа Instagram {}: {}", mediaId, e.getMessage());
            return null;
        }
    }
}
