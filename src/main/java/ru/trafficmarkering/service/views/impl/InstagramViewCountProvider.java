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
import ru.trafficmarkering.service.views.VideoMetrics;
import ru.trafficmarkering.service.views.ViewCount;
import ru.trafficmarkering.service.views.ViewCountProvider;
import ru.trafficmarkering.util.VideoUrls;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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
    /** Метрики Reels помимо просмотров; у обычного видео часть из них площадка не отдаёт — тогда без них */
    private static final String EXTRA_METRICS = "reach,likes,comments,shares,saved,ig_reels_avg_watch_time";
    private static final DateTimeFormatter GRAPH_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

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
    public Map<String, ViewCount> fetchViews(Long creatorId, Collection<String> videoUrls) {
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

        Map<String, Instant> publishedAtByUrl = new HashMap<>();
        Map<String, String> mediaIdByUrl = resolveMediaIds(token.get(), wanted, publishedAtByUrl);
        Map<String, ViewCount> result = new LinkedHashMap<>();
        mediaIdByUrl.forEach((url, mediaId) -> {
            Long views = fetchMediaViews(token.get(), mediaId);
            if (views != null) {
                VideoMetrics metrics = fetchExtraMetrics(token.get(), mediaId)
                        .withPublishedAt(publishedAtByUrl.get(url));
                result.put(url, ViewCount.total(views).withMetrics(metrics));
            }
        });
        return result;
    }

    private Map<String, String> resolveMediaIds(String token, Map<String, String> wanted,
                                                Map<String, Instant> publishedAtByUrl) {
        Map<String, String> found = new HashMap<>();
        String url = UriComponentsBuilder.fromUriString(MEDIA_URL)
                .queryParam("fields", "id,permalink,timestamp")
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
                    Instant publishedAt = parseTimestamp(JsonNode.text(media, "timestamp"));
                    if (publishedAt != null) {
                        publishedAtByUrl.put(originalUrl, publishedAt);
                    }
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

    /**
     * Охват, лайки, сохранения и среднее время просмотра. Instagram отдаёт время в миллисекундах.
     * Площадка не отдала (не Reels, старый токен) — просмотры уже есть, остальное просто пустое.
     */
    private VideoMetrics fetchExtraMetrics(String token, String mediaId) {
        String url = UriComponentsBuilder.fromUriString(String.format(INSIGHTS_URL, mediaId))
                .queryParam("metric", EXTRA_METRICS)
                .queryParam("access_token", token)
                .encode()
                .toUriString();
        Map<String, Long> values = new HashMap<>();
        try {
            for (Object item : JsonNode.array(httpClient.getJson(url, null, NAME), "data")) {
                if (!(item instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> metric = (Map<String, Object>) item;
                String name = JsonNode.text(metric, "name");
                Long value = JsonNode.number(JsonNode.firstObject(JsonNode.array(metric, "values")), "value");
                if (value == null) {
                    value = JsonNode.number(JsonNode.object(metric, "total_value"), "value");
                }
                if (name != null && value != null) {
                    values.put(name, value);
                }
            }
        } catch (Exception e) {
            log.debug("Instagram не отдал метрики медиа {}: {}", mediaId, e.getMessage());
        }
        Long watchMs = values.get("ig_reels_avg_watch_time");
        return VideoMetrics.empty()
                .withEngagement(values.get("likes"), values.get("comments"), values.get("shares"))
                .withSaves(values.get("saved"))
                .withReach(values.get("reach"))
                .withRetention(null, watchMs != null ? watchMs / 1000.0 : null, null);
    }

    /** Graph API пишет время как 2024-01-01T10:00:00+0000 — без двоеточия в зоне, Instant.parse такое не берёт. */
    private static Instant parseTimestamp(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception ignored) {
            // пробуем формат Graph API ниже
        }
        try {
            return OffsetDateTime.parse(text, GRAPH_TIMESTAMP).toInstant();
        } catch (Exception e) {
            return null;
        }
    }
}
