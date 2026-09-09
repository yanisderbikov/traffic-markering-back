package ru.trafficmarkering.service.views.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.application.ViewSource;
import ru.trafficmarkering.service.http.JsonHttpClient;
import ru.trafficmarkering.service.http.JsonNode;
import ru.trafficmarkering.service.views.ViewCountProvider;
import ru.trafficmarkering.util.VideoUrls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Log4j2
class YoutubeViewCountProvider implements ViewCountProvider {

    private static final String NAME = "YouTube";
    private static final String VIDEOS_URL = "https://www.googleapis.com/youtube/v3/videos";
    private static final int BATCH_SIZE = 50;

    private final JsonHttpClient httpClient;

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
    public Map<String, Long> fetchViews(Long creatorId, Collection<String> videoUrls) {
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

        Map<String, Long> result = new LinkedHashMap<>();
        urlToId.forEach((url, videoId) -> {
            Long views = viewsById.get(videoId);
            if (views != null) {
                result.put(url, views);
            }
        });
        return result;
    }
}
