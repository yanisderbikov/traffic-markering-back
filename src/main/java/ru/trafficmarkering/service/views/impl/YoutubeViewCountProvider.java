package ru.trafficmarkering.service.views.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.application.ViewSource;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.service.http.JsonHttpClient;
import ru.trafficmarkering.service.http.JsonNode;
import ru.trafficmarkering.service.social.SocialTokenService;
import ru.trafficmarkering.service.views.VideoMetrics;
import ru.trafficmarkering.service.views.ViewCount;
import ru.trafficmarkering.service.views.ViewCountProvider;
import ru.trafficmarkering.util.VideoUrls;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Просмотры YouTube Shorts. Data API отдаёт общий счётчик, лайки, комментарии и дату
 * публикации по ключу приложения; Analytics API по OAuth-токену криатора — географию,
 * досмотры (engagedViews) и источники трафика: на них держится антифрод.
 */
@Component
@RequiredArgsConstructor
@Log4j2
class YoutubeViewCountProvider implements ViewCountProvider {

    private static final String NAME = "YouTube";
    private static final String VIDEOS_URL = "https://www.googleapis.com/youtube/v3/videos";
    private static final String ANALYTICS_URL = "https://youtubeanalytics.googleapis.com/v2/reports";
    private static final String ANALYTICS_START_DATE = "2005-01-01";
    private static final String RETENTION_METRICS = "views,engagedViews,averageViewDuration,averageViewPercentage,shares";
    /** Если engagedViews аккаунту не отдаётся, запрашиваем остальное — досмотр важнее, чем ничего */
    private static final String RETENTION_METRICS_FALLBACK = "views,averageViewDuration,averageViewPercentage,shares";
    private static final int BATCH_SIZE = 50;
    private static final int MAX_COUNTRY_ROWS = 250;

    private final JsonHttpClient httpClient;
    private final SocialTokenService socialTokenService;
    private final GetterSocialAccount getterSocialAccount;

    @Value("${social.youtube.api-key}")
    private String apiKey;

    @Override
    public Platform platform() {
        return Platform.YOUTUBE_SHORTS;
    }

    @Override
    public ViewSource source() {
        return ViewSource.YOUTUBE_API;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    @Override
    public Map<String, ViewCount> fetchViews(Long creatorId, Collection<String> videoUrls) {
        Map<String, String> urlToId = new LinkedHashMap<>();
        for (String url : videoUrls) {
            String videoId = VideoUrls.youtubeVideoId(url);
            if (videoId == null) {
                log.warn("Не разобрали ссылку YouTube, пропускаем: {}", url);
                continue;
            }
            urlToId.put(url, videoId);
        }
        if (urlToId.isEmpty()) {
            return Map.of();
        }

        Map<String, ViewCount> countsById = new HashMap<>();
        List<String> ids = new ArrayList<>(urlToId.values());
        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            List<String> chunk = ids.subList(from, Math.min(from + BATCH_SIZE, ids.size()));
            String url = UriComponentsBuilder.fromUriString(VIDEOS_URL)
                    .queryParam("part", "statistics,snippet")
                    .queryParam("id", String.join(",", chunk))
                    .queryParam("key", apiKey)
                    .encode()
                    .toUriString();
            Map<String, Object> response = httpClient.getJson(url, null, NAME);
            for (Object item : JsonNode.array(response, "items")) {
                if (!(item instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> video = (Map<String, Object>) item;
                String videoId = JsonNode.text(video, "id");
                Map<String, Object> statistics = JsonNode.object(video, "statistics");
                Long views = JsonNode.number(statistics, "viewCount");
                if (videoId != null && views != null) {
                    VideoMetrics metrics = VideoMetrics.empty()
                            .withPublishedAt(parseInstant(JsonNode.text(JsonNode.object(video, "snippet"), "publishedAt")))
                            .withEngagement(JsonNode.number(statistics, "likeCount"),
                                    JsonNode.number(statistics, "commentCount"), null);
                    countsById.put(videoId, ViewCount.total(views).withMetrics(metrics));
                }
            }
        }

        Optional<String> analyticsToken = analyticsToken(creatorId);
        if (analyticsToken.isPresent()) {
            countsById.replaceAll((videoId, count) -> enrich(videoId, count, analyticsToken.get()));
        }

        Map<String, ViewCount> result = new LinkedHashMap<>();
        urlToId.forEach((url, videoId) -> {
            ViewCount count = countsById.get(videoId);
            if (count != null) {
                result.put(url, count);
            }
        });
        return result;
    }

    private Optional<String> analyticsToken(Long creatorId) {
        Optional<SocialAccount> account =
                getterSocialAccount.getActiveByUserIdAndPlatform(creatorId, Platform.YOUTUBE_SHORTS);
        if (account.isEmpty() || !account.get().reportsViewGeography()) {
            log.debug("У криатора {} нет привязки YouTube с доступом к аналитике, география просмотров неизвестна",
                    creatorId);
            return Optional.empty();
        }
        Optional<String> token = socialTokenService.accessToken(creatorId, Platform.YOUTUBE_SHORTS);
        if (token.isEmpty()) {
            log.debug("У криатора {} нет живого токена YouTube, география просмотров неизвестна", creatorId);
        }
        return token;
    }

    /** Три отчёта Analytics: страны, удержание, источники трафика. Каждый падает независимо. */
    private ViewCount enrich(String videoId, ViewCount count, String token) {
        ViewCount enriched = count;
        try {
            enriched = ViewCount.withCountries(count.total(), countryViewsFor(token, videoId))
                    .withMetrics(count.metrics());
        } catch (Exception e) {
            log.warn("Не удалось получить географию просмотров YouTube для ролика {}: {}", videoId, e.getMessage());
        }
        VideoMetrics metrics = enriched.metrics();
        try {
            metrics = retentionFor(token, videoId, metrics);
        } catch (Exception e) {
            log.warn("Не удалось получить удержание YouTube для ролика {}: {}", videoId, e.getMessage());
        }
        try {
            metrics = metrics.withTrafficSources(trafficSourcesFor(token, videoId));
        } catch (Exception e) {
            log.warn("Не удалось получить источники трафика YouTube для ролика {}: {}", videoId, e.getMessage());
        }
        return enriched.withMetrics(metrics);
    }

    private Map<String, Long> countryViewsFor(String token, String videoId) {
        String url = analyticsQuery(videoId)
                .queryParam("metrics", "views")
                .queryParam("dimensions", "country")
                .queryParam("sort", "-views")
                .queryParam("maxResults", MAX_COUNTRY_ROWS)
                .encode()
                .toUriString();
        return parseDimensionRows(httpClient.getJson(url, token, NAME));
    }

    private Map<String, Long> trafficSourcesFor(String token, String videoId) {
        String url = analyticsQuery(videoId)
                .queryParam("metrics", "views")
                .queryParam("dimensions", "insightTrafficSourceType")
                .queryParam("sort", "-views")
                .encode()
                .toUriString();
        return parseDimensionRows(httpClient.getJson(url, token, NAME));
    }

    private VideoMetrics retentionFor(String token, String videoId, VideoMetrics metrics) {
        Map<String, Object> response;
        try {
            response = httpClient.getJson(retentionUrl(videoId, RETENTION_METRICS), token, NAME);
        } catch (Exception e) {
            log.debug("YouTube не отдал engagedViews для ролика {}, запрашиваем без него: {}", videoId, e.getMessage());
            response = httpClient.getJson(retentionUrl(videoId, RETENTION_METRICS_FALLBACK), token, NAME);
        }
        Map<String, Number> values = parseMetricRow(response);
        Number engaged = values.get("engagedViews");
        Number duration = values.get("averageViewDuration");
        Number percentage = values.get("averageViewPercentage");
        Number shares = values.get("shares");
        VideoMetrics result = metrics.withRetention(
                engaged != null ? engaged.longValue() : null,
                duration != null ? duration.doubleValue() : null,
                percentage != null ? percentage.doubleValue() : null);
        if (shares != null) {
            result = result.withEngagement(result.likes(), result.comments(), shares.longValue());
        }
        return result;
    }

    private String retentionUrl(String videoId, String metrics) {
        return analyticsQuery(videoId)
                .queryParam("metrics", metrics)
                .encode()
                .toUriString();
    }

    private UriComponentsBuilder analyticsQuery(String videoId) {
        return UriComponentsBuilder.fromUriString(ANALYTICS_URL)
                .queryParam("ids", "channel==MINE")
                .queryParam("startDate", ANALYTICS_START_DATE)
                .queryParam("endDate", LocalDate.now(ZoneOffset.UTC))
                .queryParam("filters", "video==" + videoId);
    }

    /** Строки вида [измерение, просмотры] → карта; мусорные строки пропускаются. */
    private static Map<String, Long> parseDimensionRows(Map<String, Object> response) {
        Map<String, Long> byKey = new HashMap<>();
        for (Object row : JsonNode.array(response, "rows")) {
            if (!(row instanceof List<?> cells) || cells.size() < 2) {
                continue;
            }
            if (cells.get(0) instanceof String key && !key.isBlank() && cells.get(1) instanceof Number views) {
                byKey.merge(key.trim(), views.longValue(), Long::sum);
            }
        }
        return byKey;
    }

    /** Отчёт без измерений: одна строка значений в порядке columnHeaders. */
    private static Map<String, Number> parseMetricRow(Map<String, Object> response) {
        List<String> names = new ArrayList<>();
        for (Object header : JsonNode.array(response, "columnHeaders")) {
            if (header instanceof Map<?, ?> map && map.get("name") instanceof String name) {
                names.add(name);
            }
        }
        Map<String, Number> values = new HashMap<>();
        List<Object> rows = JsonNode.array(response, "rows");
        if (rows.isEmpty() || !(rows.get(0) instanceof List<?> cells)) {
            return values;
        }
        for (int i = 0; i < names.size() && i < cells.size(); i++) {
            if (cells.get(i) instanceof Number number) {
                values.put(names.get(i), number);
            }
        }
        return values;
    }

    private static Instant parseInstant(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception e) {
            return null;
        }
    }
}
