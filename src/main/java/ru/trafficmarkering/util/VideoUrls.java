package ru.trafficmarkering.util;

import ru.trafficmarkering.model.application.Platform;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VideoUrls {

    private static final Pattern YOUTUBE_PATH = Pattern.compile(
            "/(?:shorts|embed|live|v)/([A-Za-z0-9_-]{6,64})");
    private static final Pattern YOUTUBE_WATCH = Pattern.compile(
            "[?&]v=([A-Za-z0-9_-]{6,64})");
    private static final Pattern YOUTUBE_SHORT_HOST = Pattern.compile(
            "^/([A-Za-z0-9_-]{6,64})");
    private static final Pattern TIKTOK_VIDEO = Pattern.compile(
            "/video/(\\d{6,32})");

    private VideoUrls() {
    }

    public static Platform detectPlatform(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        URI uri = parse(url.trim());
        if (uri == null || uri.getHost() == null) {
            return null;
        }
        String host = uri.getHost().toLowerCase();
        if (hostMatches(host, "youtube.com") || hostMatches(host, "youtu.be")) {
            return Platform.YOUTUBE_SHORTS;
        }
        if (hostMatches(host, "tiktok.com")) {
            return Platform.TIKTOK;
        }
        if (hostMatches(host, "instagram.com") || hostMatches(host, "instagr.am")) {
            return Platform.INSTAGRAM;
        }
        return null;
    }

    private static boolean hostMatches(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    public static String videoKey(Platform platform, String url) {
        if (platform == null || url == null || url.isBlank()) {
            return null;
        }
        String identifier = switch (platform) {
            case YOUTUBE_SHORTS -> youtubeVideoId(url);
            case TIKTOK -> tiktokVideoId(url);
            default -> null;
        };
        if (identifier == null) {
            identifier = canonical(url);
        }
        if (identifier == null) {
            identifier = url.trim().toLowerCase();
        }
        return platform.name() + ":" + identifier;
    }

    public static String youtubeVideoId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        Matcher path = YOUTUBE_PATH.matcher(trimmed);
        if (path.find()) {
            return path.group(1);
        }
        Matcher watch = YOUTUBE_WATCH.matcher(trimmed);
        if (watch.find()) {
            return watch.group(1);
        }
        URI uri = parse(trimmed);
        if (uri != null && uri.getHost() != null && uri.getHost().endsWith("youtu.be")) {
            Matcher shortHost = YOUTUBE_SHORT_HOST.matcher(uri.getPath() == null ? "" : uri.getPath());
            if (shortHost.find()) {
                return shortHost.group(1);
            }
        }
        return null;
    }

    public static String tiktokVideoId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher matcher = TIKTOK_VIDEO.matcher(url.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    public static String canonical(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        URI uri = parse(url.trim());
        if (uri == null || uri.getHost() == null) {
            return null;
        }
        String host = uri.getHost().toLowerCase();
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return host + path;
    }

    private static URI parse(String url) {
        try {
            return URI.create(url.contains("://") ? url : "https://" + url);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
