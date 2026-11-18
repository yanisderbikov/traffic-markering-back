package ru.trafficmarkering.service.views;

import java.time.Instant;
import java.util.Map;

/**
 * Что площадка знает о ролике помимо счётчика просмотров. Любое поле может быть null:
 * TikTok не отдаёт ни охват, ни удержание, Instagram — источники трафика, YouTube без
 * аналитики — ничего, кроме лайков, комментариев и даты публикации.
 */
public record VideoMetrics(
        Instant publishedAt,
        Long likes,
        Long comments,
        Long shares,
        Long saves,
        Long reach,
        Long engagedViews,
        Double avgWatchSeconds,
        Double avgViewPercentage,
        Map<String, Long> trafficSources
) {
    public static VideoMetrics empty() {
        return new VideoMetrics(null, null, null, null, null, null, null, null, null, null);
    }

    public VideoMetrics withPublishedAt(Instant value) {
        return new VideoMetrics(value, likes, comments, shares, saves, reach, engagedViews,
                avgWatchSeconds, avgViewPercentage, trafficSources);
    }

    public VideoMetrics withEngagement(Long likes, Long comments, Long shares) {
        return new VideoMetrics(publishedAt, likes, comments, shares, saves, reach, engagedViews,
                avgWatchSeconds, avgViewPercentage, trafficSources);
    }

    public VideoMetrics withSaves(Long value) {
        return new VideoMetrics(publishedAt, likes, comments, shares, value, reach, engagedViews,
                avgWatchSeconds, avgViewPercentage, trafficSources);
    }

    public VideoMetrics withReach(Long value) {
        return new VideoMetrics(publishedAt, likes, comments, shares, saves, value, engagedViews,
                avgWatchSeconds, avgViewPercentage, trafficSources);
    }

    public VideoMetrics withRetention(Long engagedViews, Double avgWatchSeconds, Double avgViewPercentage) {
        return new VideoMetrics(publishedAt, likes, comments, shares, saves, reach, engagedViews,
                avgWatchSeconds, avgViewPercentage, trafficSources);
    }

    public VideoMetrics withTrafficSources(Map<String, Long> value) {
        return new VideoMetrics(publishedAt, likes, comments, shares, saves, reach, engagedViews,
                avgWatchSeconds, avgViewPercentage, value);
    }
}
