package ru.trafficmarkering.service.scrape;

import ru.trafficmarkering.model.application.Platform;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Что ввёл креатор вместо OAuth: ник с @ или без, либо ссылку на профиль.
 * Для YouTube ещё бывает id канала (UC…) и старые ссылки /c/… и /user/… — их отдаём
 * в ScrapeCreators ссылкой целиком.
 */
public record ProfileRef(Kind kind, String value) {

    public enum Kind { HANDLE, CHANNEL_ID, URL }

    private static final Pattern TIKTOK_HANDLE = Pattern.compile("[A-Za-z0-9._]{2,24}");
    private static final Pattern INSTAGRAM_HANDLE = Pattern.compile("[A-Za-z0-9._]{1,30}");
    private static final Pattern YOUTUBE_HANDLE = Pattern.compile("[A-Za-z0-9._\\-]{3,30}");
    private static final Pattern YOUTUBE_CHANNEL_ID = Pattern.compile("UC[A-Za-z0-9_\\-]{22}");
    private static final List<String> INSTAGRAM_RESERVED =
            List.of("p", "reel", "reels", "tv", "stories", "explore", "accounts", "direct");

    public static Optional<ProfileRef> parse(Platform platform, String input) {
        if (input == null || platform == null) {
            return Optional.empty();
        }
        String raw = input.trim();
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        if (looksLikeUrl(raw)) {
            return fromUrl(platform, raw);
        }
        String handle = raw.startsWith("@") ? raw.substring(1) : raw;
        if (platform == Platform.YOUTUBE_SHORTS && YOUTUBE_CHANNEL_ID.matcher(handle).matches()) {
            return Optional.of(new ProfileRef(Kind.CHANNEL_ID, handle));
        }
        return handle(platform, handle);
    }

    /** Ник для сообщений креатору: «@nick», а для id канала и ссылок — как есть. */
    public String label() {
        return kind == Kind.HANDLE ? "@" + value : value;
    }

    private static Optional<ProfileRef> fromUrl(Platform platform, String raw) {
        URI uri;
        try {
            uri = URI.create(raw.contains("://") ? raw : "https://" + raw);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String[] segments = uri.getPath() == null ? new String[0] : uri.getPath().replaceAll("^/+", "").split("/+");
        String first = segments.length > 0 ? segments[0] : "";
        return switch (platform) {
            case TIKTOK -> host.endsWith("tiktok.com") && first.startsWith("@")
                    ? handle(platform, first.substring(1))
                    : Optional.empty();
            case INSTAGRAM -> (host.endsWith("instagram.com") || host.endsWith("instagr.am"))
                    && !first.isEmpty() && !INSTAGRAM_RESERVED.contains(first.toLowerCase(Locale.ROOT))
                    ? handle(platform, first)
                    : Optional.empty();
            case YOUTUBE_SHORTS -> youtubeUrl(host, segments, uri);
            default -> Optional.empty();
        };
    }

    private static Optional<ProfileRef> youtubeUrl(String host, String[] segments, URI uri) {
        if (!host.endsWith("youtube.com") || segments.length == 0 || segments[0].isEmpty()) {
            return Optional.empty();
        }
        String first = segments[0];
        if (first.startsWith("@")) {
            return handle(Platform.YOUTUBE_SHORTS, first.substring(1));
        }
        if ("channel".equals(first) && segments.length > 1 && YOUTUBE_CHANNEL_ID.matcher(segments[1]).matches()) {
            return Optional.of(new ProfileRef(Kind.CHANNEL_ID, segments[1]));
        }
        if (("c".equals(first) || "user".equals(first)) && segments.length > 1) {
            return Optional.of(new ProfileRef(Kind.URL, "https://www.youtube.com/" + first + "/" + segments[1]));
        }
        return Optional.empty();
    }

    private static Optional<ProfileRef> handle(Platform platform, String handle) {
        Pattern pattern = switch (platform) {
            case TIKTOK -> TIKTOK_HANDLE;
            case INSTAGRAM -> INSTAGRAM_HANDLE;
            case YOUTUBE_SHORTS -> YOUTUBE_HANDLE;
            default -> null;
        };
        if (pattern == null || !pattern.matcher(handle).matches()) {
            return Optional.empty();
        }
        return Optional.of(new ProfileRef(Kind.HANDLE, handle));
    }

    private static boolean looksLikeUrl(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        return lower.contains("://") || lower.contains("/") || lower.matches("^(www\\.|m\\.)?[a-z0-9.-]+\\.(com|am)(/.*)?$");
    }
}
