package ru.trafficmarkering.service.scrape;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.service.http.JsonNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ручки ScrapeCreators, которые нужны сервису, с разбором ответа в общие записи.
 * Каждый успешный запрос стоит 1 кредит; кэш ScrapeCreators (cache_max_age) не используем:
 * и описание профиля при проверке кода, и счётчики роликов нужны свежими.
 *
 * <ul>
 *     <li>TikTok: /v1/tiktok/profile, /v2/tiktok/video</li>
 *     <li>Instagram: /v1/instagram/profile, /v1/instagram/post</li>
 *     <li>YouTube: /v1/youtube/channel, /v1/youtube/video</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ScrapeCreatorsApi {

    private final ScrapeCreatorsClient client;

    public boolean isConfigured() {
        return client.isConfigured();
    }

    public ScrapeProfile profile(Platform platform, ProfileRef ref) {
        return switch (platform) {
            case TIKTOK -> tiktokProfile(ref);
            case INSTAGRAM -> instagramProfile(ref);
            case YOUTUBE_SHORTS -> youtubeChannel(ref);
            default -> throw new IllegalArgumentException("ScrapeCreators не поддерживает площадку " + platform);
        };
    }

    public ScrapeVideo video(Platform platform, String videoUrl) {
        return switch (platform) {
            case TIKTOK -> tiktokVideo(videoUrl);
            case INSTAGRAM -> instagramPost(videoUrl);
            case YOUTUBE_SHORTS -> youtubeVideo(videoUrl);
            default -> throw new IllegalArgumentException("ScrapeCreators не поддерживает площадку " + platform);
        };
    }

    /**
     * Прямая ссылка на файл ролика (для бота модерации, когда yt-dlp не справился). TikTok — play_addr
     * без водяного знака либо download_addr, Instagram — video_url; YouTube прямых ссылок не отдаёт.
     * Стоит кредит, поэтому зовётся только запасным вариантом.
     */
    public Optional<String> videoFileUrl(Platform platform, String videoUrl) {
        return switch (platform) {
            case TIKTOK -> {
                Map<String, Object> video = JsonNode.object(JsonNode.object(
                        client.get("/v2/tiktok/video", Map.of("url", videoUrl)), "aweme_detail"), "video");
                yield firstUrl(video, "play_addr", "download_addr", "play_addr_h264");
            }
            case INSTAGRAM -> Optional.ofNullable(JsonNode.text(JsonNode.object(JsonNode.object(
                    client.get("/v1/instagram/post", Map.of("url", videoUrl)), "data"), "xdt_shortcode_media"), "video_url"));
            default -> Optional.empty();
        };
    }

    private static Optional<String> firstUrl(Map<String, Object> video, String... keys) {
        for (String key : keys) {
            for (Object url : JsonNode.array(JsonNode.object(video, key), "url_list")) {
                if (url instanceof String text && text.startsWith("http")) {
                    return Optional.of(text);
                }
            }
        }
        return Optional.empty();
    }

    private ScrapeProfile tiktokProfile(ProfileRef ref) {
        Map<String, Object> response = client.get("/v1/tiktok/profile", Map.of("handle", ref.value()));
        Map<String, Object> user = JsonNode.object(response, "user");
        Map<String, Object> stats = JsonNode.object(response, "stats");
        String id = JsonNode.text(user, "id");
        if (id == null) {
            throw new ScrapeCreatorsException(404, "TikTok не отдал профиль " + ref.label());
        }
        return new ScrapeProfile(
                id,
                JsonNode.text(user, "uniqueId"),
                JsonNode.text(user, "nickname"),
                firstText(user, "avatarMedium", "avatarLarger", "avatarThumb"),
                JsonNode.number(stats, "followerCount"),
                JsonNode.text(user, "signature"),
                privacy(user.get("privateAccount"), user.get("secret")),
                tiktokLinks(user));
    }

    private ScrapeVideo tiktokVideo(String videoUrl) {
        Map<String, Object> response = client.get("/v2/tiktok/video", Map.of("url", videoUrl));
        Map<String, Object> video = JsonNode.object(response, "aweme_detail");
        Map<String, Object> statistics = JsonNode.object(video, "statistics");
        Map<String, Object> author = JsonNode.object(video, "author");
        if (video.isEmpty()) {
            throw new ScrapeCreatorsException(404, "TikTok не отдал ролик");
        }
        return new ScrapeVideo(
                JsonNode.text(author, "uid"),
                JsonNode.text(author, "unique_id"),
                JsonNode.number(statistics, "play_count"),
                JsonNode.number(statistics, "digg_count"),
                JsonNode.number(statistics, "comment_count"),
                JsonNode.number(statistics, "share_count"),
                JsonNode.number(statistics, "collect_count"),
                epochSeconds(JsonNode.number(video, "create_time")));
    }

    private ScrapeProfile instagramProfile(ProfileRef ref) {
        Map<String, Object> response = client.get("/v1/instagram/profile", Map.of("handle", ref.value()));
        Map<String, Object> user = JsonNode.object(JsonNode.object(response, "data"), "user");
        String id = JsonNode.text(user, "id");
        if (id == null) {
            throw new ScrapeCreatorsException(404, "Instagram не отдал профиль " + ref.label());
        }
        return new ScrapeProfile(
                id,
                JsonNode.text(user, "username"),
                JsonNode.text(user, "full_name"),
                firstText(user, "profile_pic_url_hd", "profile_pic_url"),
                JsonNode.number(JsonNode.object(user, "edge_followed_by"), "count"),
                JsonNode.text(user, "biography"),
                privacy(user.get("is_private")),
                instagramLinks(user));
    }

    /**
     * video_play_count — основной счётчик Instagram, video_view_count — запасной.
     * Комментарии в ответе лежат в одном из трёх edge — какой есть, тот и берём.
     */
    private ScrapeVideo instagramPost(String videoUrl) {
        Map<String, Object> response = client.get("/v1/instagram/post", Map.of("url", videoUrl));
        Map<String, Object> media = JsonNode.object(JsonNode.object(response, "data"), "xdt_shortcode_media");
        if (media.isEmpty()) {
            throw new ScrapeCreatorsException(404, "Instagram не отдал публикацию");
        }
        Map<String, Object> owner = JsonNode.object(media, "owner");
        Long views = JsonNode.number(media, "video_play_count");
        if (views == null || views == 0) {
            Long fallback = JsonNode.number(media, "video_view_count");
            views = fallback != null && fallback > 0 ? fallback : views;
        }
        return new ScrapeVideo(
                JsonNode.text(owner, "id"),
                JsonNode.text(owner, "username"),
                views,
                firstCount(media, "edge_media_preview_like", "edge_liked_by"),
                firstCount(media, "edge_media_to_parent_comment", "edge_media_to_comment", "edge_media_preview_comment"),
                null,
                null,
                epochSeconds(JsonNode.number(media, "taken_at_timestamp")));
    }

    private ScrapeProfile youtubeChannel(ProfileRef ref) {
        Map<String, String> params = new LinkedHashMap<>();
        switch (ref.kind()) {
            case HANDLE -> params.put("handle", ref.value());
            case CHANNEL_ID -> params.put("channelId", ref.value());
            case URL -> params.put("url", ref.value());
        }
        Map<String, Object> response = client.get("/v1/youtube/channel", params);
        String channelId = JsonNode.text(response, "channelId");
        if (channelId == null) {
            throw new ScrapeCreatorsException(404, "YouTube не отдал канал " + ref.label());
        }
        return new ScrapeProfile(
                channelId,
                youtubeHandle(JsonNode.text(response, "channel"), ref),
                JsonNode.text(response, "name"),
                lastSource(JsonNode.object(JsonNode.object(response, "avatar"), "image")),
                JsonNode.number(response, "subscriberCount"),
                JsonNode.text(response, "description"),
                ScrapeProfile.Privacy.PUBLIC,
                textArray(response, "links"));
    }

    private ScrapeVideo youtubeVideo(String videoUrl) {
        Map<String, Object> response = client.get("/v1/youtube/video", Map.of("url", videoUrl));
        if (JsonNode.text(response, "id") == null) {
            throw new ScrapeCreatorsException(404, "YouTube не отдал ролик");
        }
        Map<String, Object> channel = JsonNode.object(response, "channel");
        String channelId = JsonNode.text(channel, "id");
        if (channelId == null) {
            channelId = JsonNode.text(JsonNode.object(response, "ownerByline"), "channelId");
        }
        Long views = JsonNode.number(response, "viewCountInt");
        if (views == null) {
            views = digits(JsonNode.text(response, "viewCountText"));
        }
        return new ScrapeVideo(
                channelId,
                JsonNode.text(channel, "handle"),
                views,
                JsonNode.number(response, "likeCountInt"),
                JsonNode.number(response, "commentCountInt"),
                null,
                null,
                parseOffset(JsonNode.text(response, "publishDate")));
    }

    /** TikTok: ссылка в профиле лежит в user.bioLink.link; нет bioLink в ответе — источник ссылок не знает. */
    private static List<String> tiktokLinks(Map<String, Object> user) {
        if (!user.containsKey("bioLink")) {
            return null;
        }
        List<String> links = new ArrayList<>();
        addLink(links, JsonNode.text(JsonNode.object(user, "bioLink"), "link"));
        return links;
    }

    /** Instagram: bio_links[].url и external_url; нет ни одного из полей — источник ссылок не знает. */
    private static List<String> instagramLinks(Map<String, Object> user) {
        if (!user.containsKey("bio_links") && !user.containsKey("external_url")) {
            return null;
        }
        List<String> links = new ArrayList<>();
        addLink(links, JsonNode.text(user, "external_url"));
        for (Object item : JsonNode.array(user, "bio_links")) {
            if (item instanceof Map<?, ?> link && link.get("url") instanceof String url) {
                addLink(links, url);
            }
        }
        return links;
    }

    /** Массив строк (ссылки канала YouTube); нет поля — null, а не пустой список. */
    private static List<String> textArray(Map<String, Object> source, String key) {
        if (source == null || !source.containsKey(key)) {
            return null;
        }
        List<Object> items = JsonNode.array(source, key);
        List<String> values = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof String text) {
                addLink(values, text);
            } else if (item instanceof Map<?, ?> object) {
                for (String field : List.of("url", "link", "href")) {
                    if (object.get(field) instanceof String text) {
                        addLink(values, text);
                        break;
                    }
                }
            }
        }
        // Незнакомый формат элементов — не «ссылок нет», а «не разобрали»
        return !items.isEmpty() && values.isEmpty() ? null : values;
    }

    private static void addLink(List<String> links, String link) {
        if (link != null && !link.isBlank() && !links.contains(link.trim())) {
            links.add(link.trim());
        }
    }

    /** «http://www.youtube.com/@ThePatMcAfeeShow» → ThePatMcAfeeShow; нет @ в ссылке — то, что ввёл креатор. */
    private static String youtubeHandle(String channelUrl, ProfileRef ref) {
        if (channelUrl != null) {
            int at = channelUrl.lastIndexOf("/@");
            if (at >= 0) {
                String handle = channelUrl.substring(at + 2).replaceAll("[/?#].*$", "");
                if (!handle.isBlank()) {
                    return handle;
                }
            }
        }
        return ref.kind() == ProfileRef.Kind.HANDLE ? ref.value() : null;
    }

    /** Аватары YouTube идут от меньшего к большему, берём последний. */
    private static String lastSource(Map<String, Object> image) {
        List<Object> sources = JsonNode.array(image, "sources");
        for (int i = sources.size() - 1; i >= 0; i--) {
            if (sources.get(i) instanceof Map<?, ?> source && source.get("url") instanceof String url && !url.isBlank()) {
                return url;
            }
        }
        return null;
    }

    private static Long firstCount(Map<String, Object> media, String... edges) {
        for (String edge : edges) {
            Long count = JsonNode.number(JsonNode.object(media, edge), "count");
            if (count != null) {
                return count;
            }
        }
        return null;
    }

    private static String firstText(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            String text = JsonNode.text(source, key);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    /** Хоть один признак true — закрыт; все признаки false — открыт; признаков в ответе нет — неизвестно. */
    private static ScrapeProfile.Privacy privacy(Object... flags) {
        boolean known = false;
        for (Object flag : flags) {
            Boolean value = flag(flag);
            if (Boolean.TRUE.equals(value)) {
                return ScrapeProfile.Privacy.PRIVATE;
            }
            known |= value != null;
        }
        return known ? ScrapeProfile.Privacy.PUBLIC : ScrapeProfile.Privacy.UNKNOWN;
    }

    private static Boolean flag(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if ("true".equals(value) || Integer.valueOf(1).equals(value)) {
            return true;
        }
        if ("false".equals(value) || Integer.valueOf(0).equals(value)) {
            return false;
        }
        return null;
    }

    private static Instant epochSeconds(Long seconds) {
        return seconds != null && seconds > 0 ? Instant.ofEpochSecond(seconds) : null;
    }

    private static Instant parseOffset(String text) {
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (Exception e) {
            try {
                return Instant.parse(text);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static Long digits(String text) {
        if (text == null) {
            return null;
        }
        String digits = text.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
