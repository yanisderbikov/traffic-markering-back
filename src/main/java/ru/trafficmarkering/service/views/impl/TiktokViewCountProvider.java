package ru.trafficmarkering.service.views.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.application.ViewSource;
import ru.trafficmarkering.service.http.JsonHttpClient;
import ru.trafficmarkering.service.http.JsonNode;
import ru.trafficmarkering.service.social.SocialTokenService;
import ru.trafficmarkering.service.views.ViewCountProvider;
import ru.trafficmarkering.util.VideoUrls;

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
class TiktokViewCountProvider implements ViewCountProvider {

    private static final String NAME = "TikTok";
    private static final String QUERY_URL =
            "https://open.tiktokapis.com/v2/video/query/?fields=id,view_count";
    private static final int BATCH_SIZE = 20;

    private final JsonHttpClient httpClient;
    private final SocialTokenService socialTokenService;

    @Override
    public Platform platform() {
        return Platform.TIKTOK;
    }

    @Override
    public ViewSource source() {
        return ViewSource.TIKTOK_API;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public Map<String, Long> fetchViews(Long creatorId, Collection<String> videoUrls) {
        Optional<String> token = socialTokenService.accessToken(creatorId, Platform.TIKTOK);
        if (token.isEmpty()) {
            log.debug("У криатора {} нет живого токена TikTok, пропускаем {} роликов",
                    creatorId, videoUrls.size());
            return Map.of();
        }

        Map<String, String> urlToId = new LinkedHashMap<>();
        for (String url : videoUrls) {
            String videoId = VideoUrls.tiktokVideoId(url);
            if (videoId == null) {
                log.warn("Не разобрали ссылку TikTok, пропускаем: {}", url);
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
            Map<String, Object> body = Map.of("filters", Map.of("video_ids", chunk));
            Map<String, Object> response = httpClient.postJson(QUERY_URL, body, token.get(), NAME);
            for (Object item : JsonNode.array(JsonNode.object(response, "data"), "videos")) {
                if (!(item instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> video = (Map<String, Object>) item;
                String videoId = JsonNode.text(video, "id");
                Long views = JsonNode.number(video, "view_count");
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
