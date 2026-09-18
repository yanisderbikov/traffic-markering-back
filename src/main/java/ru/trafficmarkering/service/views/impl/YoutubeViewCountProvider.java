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
import ru.trafficmarkering.service.views.ViewCount;
import ru.trafficmarkering.service.views.ViewCountProvider;
import ru.trafficmarkering.util.VideoUrls;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Log4j2
class YoutubeViewCountProvider implements ViewCountProvider {

    private static final String NAME = "YouTube";
    private static final String VIDEOS_URL = "https://www.googleapis.com/youtube/v3/videos";
    private static final String ANALYTICS_URL = "https://youtubeanalytics.googleapis.com/v2/reports";
    private static final String ANALYTICS_START_DATE = "2005-01-01";
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

        Map<String, Long> viewsById = new HashMap<>();
        List<String> ids = new ArrayList<>(urlToId.values());
        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            List<String> chunk = ids.subList(from, Math.min(from + BATCH_SIZE, ids.size()));
            String url = UriComponentsBuilder.fromUriString(VIDEOS_URL)
                    .queryParam("part", "statistics")
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
                Long views = JsonNode.number(JsonNode.object(video, "statistics"), "viewCount");
                if (videoId != null && views != null) {
                    viewsById.put(videoId, views);
                }
            }
        }

        Optional<String> analyticsToken = analyticsToken(creatorId);

        Map<String, ViewCount> countsById = new HashMap<>();
        Map<String, ViewCount> result = new LinkedHashMap<>();
        urlToId.forEach((url, videoId) -> {
            Long views = viewsById.get(videoId);
            if (views != null) {
                result.put(url, countsById.computeIfAbsent(videoId,
                        id -> viewCountFor(id, views, analyticsToken)));
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

    private ViewCount viewCountFor(String videoId, long total, Optional<String> analyticsToken) {
        if (analyticsToken.isEmpty()) {
            return ViewCount.total(total);
        }
        try {
            return ViewCount.withCountries(total, countryViewsFor(analyticsToken.get(), videoId));
        } catch (Exception e) {
            log.warn("Не удалось получить географию просмотров YouTube для ролика {}: {}",
                    videoId, e.getMessage());
            return ViewCount.total(total);
        }
    }

    private Map<String, Long> countryViewsFor(String token, String videoId) {
        String url = UriComponentsBuilder.fromUriString(ANALYTICS_URL)
                .queryParam("ids", "channel==MINE")
                .queryParam("startDate", ANALYTICS_START_DATE)
                .queryParam("endDate", LocalDate.now(ZoneOffset.UTC))
                .queryParam("metrics", "views")
                .queryParam("dimensions", "country")
                .queryParam("filters", "video==" + videoId)
                .queryParam("sort", "-views")
                .queryParam("maxResults", MAX_COUNTRY_ROWS)
                .encode()
                .toUriString();
        return parseCountryRows(httpClient.getJson(url, token, NAME));
    }

    private static Map<String, Long> parseCountryRows(Map<String, Object> response) {
        Map<String, Long> countryViews = new HashMap<>();
        for (Object row : JsonNode.array(response, "rows")) {
            if (!(row instanceof List<?> cells) || cells.size() < 2) {
                continue;
            }
            if (cells.get(0) instanceof String country && !country.isBlank()
                    && cells.get(1) instanceof Number views) {
                countryViews.merge(country.trim(), views.longValue(), Long::sum);
            }
        }
        return countryViews;
    }
}
