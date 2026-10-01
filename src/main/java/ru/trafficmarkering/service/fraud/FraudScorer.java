package ru.trafficmarkering.service.fraud;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.config.FraudProperties;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.fraud.FraudFlag;
import ru.trafficmarkering.model.fraud.FraudStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Правила антифрода. Чистая логика без базы: на вход отклик, объявление, история замеров
 * и подписчики криатора, на выход баллы и флаги. Каждое правило независимо, баллы
 * складываются и режутся сотней; пороги статусов — в {@link FraudProperties}.
 */
@Component
@RequiredArgsConstructor
public class FraudScorer {

    public static final String VIEWS_DROPPED = "VIEWS_DROPPED";
    public static final String OLD_VIDEO = "OLD_VIDEO";
    public static final String STALE_VIDEO = "STALE_VIDEO";
    public static final String LOW_ENGAGEMENT = "LOW_ENGAGEMENT";
    public static final String VELOCITY_SPIKE = "VELOCITY_SPIKE";
    public static final String LOW_RETENTION = "LOW_RETENTION";
    public static final String REPLAY_HEAVY = "REPLAY_HEAVY";
    public static final String SUSPICIOUS_TRAFFIC = "SUSPICIOUS_TRAFFIC";
    public static final String UNKNOWN_GEOGRAPHY = "UNKNOWN_GEOGRAPHY";
    public static final String VIEWS_VS_FOLLOWERS = "VIEWS_VS_FOLLOWERS";

    /** Источники трафика YouTube, откуда органика в Shorts не приходит */
    private static final Set<String> NON_ORGANIC_SOURCES =
            Set.of("EXT_URL", "NO_LINK_OTHER", "NO_LINK_EMBEDDED", "ADVERTISING", "PROMOTED", "CAMPAIGN_CARD");

    private static final Duration VELOCITY_WINDOW = Duration.ofDays(7);
    private static final Duration MAX_INTERVAL = Duration.ofHours(3);
    private static final Duration WARM_UP = Duration.ofHours(48);
    private static final Duration STALE_AFTER = Duration.ofDays(14);

    private final FraudProperties properties;

    /**
     * @param snapshots история замеров в любом порядке
     * @param followers подписчики аккаунта криатора на площадке ролика; null — неизвестно
     */
    public FraudAssessment assess(Application application,
                                  Campaign campaign,
                                  List<ApplicationViewSnapshot> snapshots,
                                  Long followers,
                                  Instant now) {
        List<ApplicationViewSnapshot> ordered = snapshots.stream()
                .filter(snapshot -> snapshot.getCapturedAt() != null)
                .sorted(Comparator.comparing(ApplicationViewSnapshot::getCapturedAt))
                .toList();
        List<FraudFlag> flags = new ArrayList<>();

        checkPublishedAt(application, campaign, flags);
        if (!ordered.isEmpty()) {
            ApplicationViewSnapshot latest = ordered.get(ordered.size() - 1);
            ApplicationViewSnapshot metrics = latestWithMetrics(ordered);
            long views = latest.totalViews();

            checkDrop(ordered, flags);
            checkVelocity(application, ordered, views, now, flags);
            if (views >= properties.getMinViewsForRatios()) {
                boolean lowEngagement = checkEngagement(metrics, views, flags);
                checkRetention(metrics, views, flags);
                checkReplays(metrics, views, flags);
                checkTrafficSources(metrics, flags);
                checkUnknownGeography(latest, views, flags);
                checkFollowers(views, followers, lowEngagement, flags);
            }
        }

        int score = Math.min(100, flags.stream().mapToInt(FraudFlag::points).sum());
        return new FraudAssessment(score, List.copyOf(flags));
    }

    public FraudStatus statusFor(int score) {
        if (score >= properties.getFraudThreshold()) {
            return FraudStatus.FRAUD;
        }
        if (score >= properties.getSuspiciousThreshold()) {
            return FraudStatus.SUSPICIOUS;
        }
        return FraudStatus.CLEAN;
    }

    /** Ролик, снятый до появления объявления, под него сниматься не мог. */
    private void checkPublishedAt(Application application, Campaign campaign, List<FraudFlag> flags) {
        Instant publishedAt = application.getVideoPublishedAt();
        if (publishedAt == null) {
            return;
        }
        if (campaign != null && campaign.getCreatedAt() != null && publishedAt.isBefore(campaign.getCreatedAt())) {
            flags.add(new FraudFlag(OLD_VIDEO, "Ролик старше объявления", 50,
                    "Ролик опубликован за " + gap(publishedAt, campaign.getCreatedAt()) + " до создания объявления"));
            return;
        }
        if (application.getCreatedAt() != null
                && publishedAt.isBefore(application.getCreatedAt().minus(STALE_AFTER))) {
            flags.add(new FraudFlag(STALE_VIDEO, "Ролик опубликован задолго до отклика", 20,
                    "Ролик опубликован за " + gap(publishedAt, application.getCreatedAt()) + " до отклика"));
        }
    }

    /** Промежуток без привязки к часовому поясу: в днях, а если меньше суток — в часах. */
    private static String gap(Instant from, Instant to) {
        Duration duration = Duration.between(from, to);
        long days = duration.toDays();
        return days > 0 ? days + " дн." : Math.max(1, duration.toHours()) + " ч.";
    }

    /** Площадка сама списала просмотры: она признала накрутку раньше нас. */
    private void checkDrop(List<ApplicationViewSnapshot> ordered, List<FraudFlag> flags) {
        long runningMax = 0L;
        long worstDrop = 0L;
        long before = 0L;
        long after = 0L;
        for (ApplicationViewSnapshot snapshot : ordered) {
            long views = snapshot.totalViews();
            if (runningMax >= 1_000 && views < runningMax * 0.95) {
                long drop = runningMax - views;
                if (drop > worstDrop) {
                    worstDrop = drop;
                    before = runningMax;
                    after = views;
                }
            }
            runningMax = Math.max(runningMax, views);
        }
        if (worstDrop > 0) {
            flags.add(new FraudFlag(VIEWS_DROPPED, "Площадка списала просмотры", 40,
                    "Было " + format(before) + ", стало " + format(after)
                            + ": площадка убрала " + format(worstDrop) + " просмотров"));
        }
    }

    /**
     * Боты приходят ступенькой: за один замер прилетает треть всего счётчика уже после того,
     * как ролик отлежал первые двое суток. Органика первых часов под правило не попадает.
     */
    private void checkVelocity(Application application,
                               List<ApplicationViewSnapshot> ordered,
                               long totalViews,
                               Instant now,
                               List<FraudFlag> flags) {
        if (totalViews < properties.getMinViewsForRatios()) {
            return;
        }
        Instant born = application.getVideoPublishedAt() != null
                ? application.getVideoPublishedAt()
                : application.getCreatedAt();
        Instant warmedUp = born != null ? born.plus(WARM_UP) : Instant.MIN;
        Instant windowStart = now.minus(VELOCITY_WINDOW);

        long worstDelta = 0L;
        Duration worstInterval = Duration.ZERO;
        for (int i = 1; i < ordered.size(); i++) {
            ApplicationViewSnapshot from = ordered.get(i - 1);
            ApplicationViewSnapshot to = ordered.get(i);
            if (to.getCapturedAt().isBefore(windowStart) || to.getCapturedAt().isBefore(warmedUp)) {
                continue;
            }
            Duration interval = Duration.between(from.getCapturedAt(), to.getCapturedAt());
            if (interval.isZero() || interval.compareTo(MAX_INTERVAL) > 0) {
                continue;
            }
            long delta = to.totalViews() - from.totalViews();
            if (delta > worstDelta) {
                worstDelta = delta;
                worstInterval = interval;
            }
        }
        if (worstDelta >= totalViews * 0.3) {
            long minutes = Math.max(1, worstInterval.toMinutes());
            flags.add(new FraudFlag(VELOCITY_SPIKE, "Резкий всплеск просмотров", 30,
                    "За " + minutes + " мин. пришло " + format(worstDelta) + " из " + format(totalViews)
                            + " просмотров (" + percent(worstDelta, totalViews) + ")"));
        }
    }

    /** Органика на вертикальных роликах даёт 3–10 % лайков к просмотрам, купленные просмотры — доли процента. */
    private boolean checkEngagement(ApplicationViewSnapshot metrics, long views, List<FraudFlag> flags) {
        if (metrics == null || metrics.getLikes() == null) {
            return false;
        }
        double ratio = (double) metrics.getLikes() / views;
        int points = ratio < 0.002 ? 35 : ratio < 0.005 ? 25 : 0;
        if (points == 0) {
            return false;
        }
        flags.add(new FraudFlag(LOW_ENGAGEMENT, "Слишком мало лайков", points,
                format(metrics.getLikes()) + " лайков на " + format(views) + " просмотров ("
                        + percent(metrics.getLikes(), views) + ")"));
        return true;
    }

    /** Боты не досматривают: у YouTube это engagedViews и процент досмотра, у Instagram — среднее время. */
    private void checkRetention(ApplicationViewSnapshot metrics, long views, List<FraudFlag> flags) {
        if (metrics == null) {
            return;
        }
        FraudFlag strongest = null;
        if (metrics.getEngagedViews() != null) {
            double ratio = (double) metrics.getEngagedViews() / views;
            if (ratio < 0.3) {
                strongest = new FraudFlag(LOW_RETENTION, "Просмотры без досмотра", 30,
                        "Досмотрено " + format(metrics.getEngagedViews()) + " из " + format(views)
                                + " (" + percent(metrics.getEngagedViews(), views) + ")");
            }
        }
        if (strongest == null && metrics.getAvgViewPercentage() != null && metrics.getAvgViewPercentage() < 15.0) {
            strongest = new FraudFlag(LOW_RETENTION, "Низкий процент досмотра", 20,
                    String.format(Locale.ROOT, "В среднем досматривают %.1f %% ролика", metrics.getAvgViewPercentage()));
        }
        if (strongest == null && metrics.getAvgWatchSeconds() != null && metrics.getAvgWatchSeconds() < 2.0) {
            strongest = new FraudFlag(LOW_RETENTION, "Ролик почти не смотрят", 20,
                    String.format(Locale.ROOT, "Среднее время просмотра %.1f с", metrics.getAvgWatchSeconds()));
        }
        if (strongest != null) {
            flags.add(strongest);
        }
    }

    /** Один аккаунт крутит ролик по кругу: просмотров в разы больше, чем охвата. */
    private void checkReplays(ApplicationViewSnapshot metrics, long views, List<FraudFlag> flags) {
        if (metrics == null || metrics.getReach() == null || metrics.getReach() <= 0) {
            return;
        }
        double ratio = (double) views / metrics.getReach();
        if (ratio > 3.0) {
            flags.add(new FraudFlag(REPLAY_HEAVY, "Много повторных просмотров", 25,
                    format(views) + " просмотров на " + format(metrics.getReach()) + " аккаунтов ("
                            + String.format(Locale.ROOT, "%.1f", ratio) + " на аккаунт)"));
        }
    }

    /** Shorts смотрят из ленты; если больше половины пришло по внешним ссылкам и рекламе — это не лента. */
    private void checkTrafficSources(ApplicationViewSnapshot metrics, List<FraudFlag> flags) {
        if (metrics == null || metrics.getTrafficSources() == null || metrics.getTrafficSources().isEmpty()) {
            return;
        }
        Map<String, Long> sources = metrics.getTrafficSources();
        long total = sources.values().stream().mapToLong(Long::longValue).sum();
        if (total <= 0) {
            return;
        }
        long nonOrganic = sources.entrySet().stream()
                .filter(entry -> NON_ORGANIC_SOURCES.contains(entry.getKey().toUpperCase(Locale.ROOT)))
                .mapToLong(Map.Entry::getValue)
                .sum();
        if (nonOrganic > total * 0.5) {
            flags.add(new FraudFlag(SUSPICIOUS_TRAFFIC, "Трафик не из ленты", 30,
                    percent(nonOrganic, total) + " просмотров пришли по внешним ссылкам и рекламе"));
        }
    }

    /** Площадка не смогла определить страну у трети зрителей: прокси и фермы обычно выглядят так. */
    private void checkUnknownGeography(ApplicationViewSnapshot latest, long views, List<FraudFlag> flags) {
        if (!latest.geographyKnown()) {
            return;
        }
        Long unknown = latest.getCountryViews().get("ZZ");
        if (unknown == null || unknown <= 0) {
            return;
        }
        if (unknown > views * 0.3) {
            flags.add(new FraudFlag(UNKNOWN_GEOGRAPHY, "Страна зрителей неизвестна", 15,
                    "У " + format(unknown) + " из " + format(views) + " просмотров (" + percent(unknown, views)
                            + ") площадка не определила страну"));
        }
    }

    /** Ролик маленького аккаунта может завируситься, но вирусный ролик собирает лайки; тут их нет. */
    private void checkFollowers(long views, Long followers, boolean lowEngagement, List<FraudFlag> flags) {
        if (!lowEngagement || followers == null || followers <= 0) {
            return;
        }
        if (views > followers * 50L) {
            flags.add(new FraudFlag(VIEWS_VS_FOLLOWERS, "Просмотры не по размеру аккаунта", 15,
                    format(views) + " просмотров при " + format(followers) + " подписчиках и без лайков"));
        }
    }

    private static ApplicationViewSnapshot latestWithMetrics(List<ApplicationViewSnapshot> ordered) {
        for (int i = ordered.size() - 1; i >= 0; i--) {
            ApplicationViewSnapshot snapshot = ordered.get(i);
            if (snapshot.getLikes() != null || snapshot.getReach() != null || snapshot.getEngagedViews() != null
                    || snapshot.getAvgWatchSeconds() != null || snapshot.getAvgViewPercentage() != null
                    || snapshot.getTrafficSources() != null) {
                return snapshot;
            }
        }
        return null;
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    private static String percent(long part, long whole) {
        if (whole <= 0) {
            return "0 %";
        }
        return String.format(Locale.ROOT, "%.1f %%", part * 100.0 / whole);
    }
}
