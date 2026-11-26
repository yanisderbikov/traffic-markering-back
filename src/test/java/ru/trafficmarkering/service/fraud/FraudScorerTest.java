package ru.trafficmarkering.service.fraud;

import org.junit.jupiter.api.Test;
import ru.trafficmarkering.config.FraudProperties;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;
import ru.trafficmarkering.model.application.ViewSource;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.fraud.FraudStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FraudScorerTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private static final Instant CAMPAIGN_CREATED = NOW.minus(Duration.ofDays(30));
    private static final Instant APPLIED = NOW.minus(Duration.ofDays(10));

    private final FraudProperties properties = new FraudProperties();
    private final FraudScorer scorer = new FraudScorer(properties);

    private final Campaign campaign = Campaign.builder().id(UUID.randomUUID()).createdAt(CAMPAIGN_CREATED).build();

    @Test
    void organicVideoIsClean() {
        // Ровный рост, 6 % лайков, досмотры, трафик из ленты — ни одно правило не срабатывает
        Application application = application(APPLIED.minus(Duration.ofDays(1)));
        List<ApplicationViewSnapshot> snapshots = steadyGrowth(application, 20_000L, 10);
        last(snapshots).setLikes(1_200L);
        last(snapshots).setEngagedViews(15_000L);
        last(snapshots).setAvgViewPercentage(65.0);
        last(snapshots).setTrafficSources(Map.of("SHORTS", 18_000L, "EXT_URL", 500L));

        FraudAssessment assessment = scorer.assess(application, campaign, snapshots, 3_000L, NOW);

        assertThat(assessment.score()).isZero();
        assertThat(assessment.flags()).isEmpty();
        assertThat(scorer.statusFor(assessment.score())).isEqualTo(FraudStatus.CLEAN);
    }

    @Test
    void platformRemovingViewsIsStrongSignal() {
        Application application = application(APPLIED);
        List<ApplicationViewSnapshot> snapshots = List.of(
                snapshot(application, NOW.minus(Duration.ofDays(3)), 50_000L),
                snapshot(application, NOW.minus(Duration.ofDays(2)), 52_000L),
                snapshot(application, NOW.minus(Duration.ofDays(1)), 31_000L));

        FraudAssessment assessment = scorer.assess(application, campaign, snapshots, null, NOW);

        assertThat(assessment.has(FraudScorer.VIEWS_DROPPED)).isTrue();
        assertThat(assessment.flags().get(0).detail()).contains("52 000").contains("31 000");
        assertThat(scorer.statusFor(assessment.score())).isEqualTo(FraudStatus.SUSPICIOUS);
    }

    @Test
    void videoOlderThanCampaignIsFlagged() {
        Application application = application(CAMPAIGN_CREATED.minus(Duration.ofDays(60)));

        FraudAssessment assessment = scorer.assess(application, campaign, List.of(), null, NOW);

        assertThat(assessment.has(FraudScorer.OLD_VIDEO)).isTrue();
        assertThat(assessment.score()).isEqualTo(50);
    }

    @Test
    void videoPublishedLongBeforeApplicationIsStale() {
        Application application = application(APPLIED.minus(Duration.ofDays(20)));

        FraudAssessment assessment = scorer.assess(application, campaign, List.of(), null, NOW);

        assertThat(assessment.has(FraudScorer.STALE_VIDEO)).isTrue();
        assertThat(assessment.has(FraudScorer.OLD_VIDEO)).isFalse();
    }

    @Test
    void lowEngagementWithTinyAccountAddsUp() {
        Application application = application(APPLIED);
        List<ApplicationViewSnapshot> snapshots = steadyGrowth(application, 100_000L, 10);
        last(snapshots).setLikes(100L);

        FraudAssessment assessment = scorer.assess(application, campaign, snapshots, 200L, NOW);

        assertThat(assessment.has(FraudScorer.LOW_ENGAGEMENT)).isTrue();
        assertThat(assessment.has(FraudScorer.VIEWS_VS_FOLLOWERS)).isTrue();
        assertThat(assessment.score()).isEqualTo(35 + 15);
        assertThat(scorer.statusFor(assessment.score())).isEqualTo(FraudStatus.SUSPICIOUS);
    }

    @Test
    void ratiosAreIgnoredBelowMinViews() {
        Application application = application(APPLIED);
        List<ApplicationViewSnapshot> snapshots = steadyGrowth(application, 2_000L, 5);
        last(snapshots).setLikes(0L);
        last(snapshots).setEngagedViews(0L);

        FraudAssessment assessment = scorer.assess(application, campaign, snapshots, 10L, NOW);

        assertThat(assessment.flags()).isEmpty();
    }

    @Test
    void spikeAfterWarmUpIsFlagged() {
        // Двое суток ролик спал, потом за час прилетело 40 % всех просмотров
        Application application = application(APPLIED);
        List<ApplicationViewSnapshot> snapshots = new ArrayList<>();
        Instant t = APPLIED;
        long views = 1_000L;
        for (int i = 0; i < 72; i++) {
            views += 50;
            t = t.plus(Duration.ofHours(1));
            snapshots.add(snapshot(application, t, views));
        }
        snapshots.add(snapshot(application, t.plus(Duration.ofHours(1)), views + 8_000L));

        FraudAssessment assessment = scorer.assess(application, campaign, snapshots, null, NOW);

        assertThat(assessment.has(FraudScorer.VELOCITY_SPIKE)).isTrue();
    }

    @Test
    void spikeInFirstHoursIsOrganic() {
        // Свежий ролик разлетелся из ленты в первые часы — это не накрутка
        Application application = application(NOW.minus(Duration.ofHours(6)));
        List<ApplicationViewSnapshot> snapshots = List.of(
                snapshot(application, NOW.minus(Duration.ofHours(5)), 100L),
                snapshot(application, NOW.minus(Duration.ofHours(4)), 200L),
                snapshot(application, NOW.minus(Duration.ofHours(3)), 9_000L),
                snapshot(application, NOW.minus(Duration.ofHours(2)), 12_000L));

        FraudAssessment assessment = scorer.assess(application, campaign, snapshots, null, NOW);

        assertThat(assessment.has(FraudScorer.VELOCITY_SPIKE)).isFalse();
    }

    @Test
    void retentionReplaysTrafficAndGeographyCombineIntoFraud() {
        Application application = application(APPLIED);
        List<ApplicationViewSnapshot> snapshots = steadyGrowth(application, 60_000L, 10);
        ApplicationViewSnapshot latest = last(snapshots);
        latest.setLikes(4_000L);
        latest.setEngagedViews(5_000L);
        latest.setReach(10_000L);
        latest.setTrafficSources(Map.of("SHORTS", 5_000L, "EXT_URL", 40_000L, "ADVERTISING", 15_000L));
        latest.setCountryViews(Map.of("RU", 20_000L, "ZZ", 40_000L));

        FraudAssessment assessment = scorer.assess(application, campaign, snapshots, 50_000L, NOW);

        assertThat(assessment.has(FraudScorer.LOW_RETENTION)).isTrue();
        assertThat(assessment.has(FraudScorer.REPLAY_HEAVY)).isTrue();
        assertThat(assessment.has(FraudScorer.SUSPICIOUS_TRAFFIC)).isTrue();
        assertThat(assessment.has(FraudScorer.UNKNOWN_GEOGRAPHY)).isTrue();
        assertThat(assessment.has(FraudScorer.LOW_ENGAGEMENT)).isFalse();
        assertThat(assessment.score()).isEqualTo(100);
        assertThat(scorer.statusFor(assessment.score())).isEqualTo(FraudStatus.FRAUD);
    }

    @Test
    void instagramShortWatchTimeCountsAsLowRetention() {
        Application application = application(APPLIED);
        List<ApplicationViewSnapshot> snapshots = steadyGrowth(application, 30_000L, 10);
        last(snapshots).setAvgWatchSeconds(1.2);

        FraudAssessment assessment = scorer.assess(application, campaign, snapshots, null, NOW);

        assertThat(assessment.has(FraudScorer.LOW_RETENTION)).isTrue();
        assertThat(assessment.score()).isEqualTo(20);
    }

    @Test
    void thresholdsComeFromProperties() {
        properties.setSuspiciousThreshold(10);
        properties.setFraudThreshold(20);

        assertThat(scorer.statusFor(9)).isEqualTo(FraudStatus.CLEAN);
        assertThat(scorer.statusFor(10)).isEqualTo(FraudStatus.SUSPICIOUS);
        assertThat(scorer.statusFor(20)).isEqualTo(FraudStatus.FRAUD);
    }

    private Application application(Instant publishedAt) {
        return Application.builder()
                .id(UUID.randomUUID())
                .campaign(campaign)
                .status(ApplicationStatus.APPROVED)
                .createdAt(APPLIED)
                .videoPublishedAt(publishedAt)
                .build();
    }

    /** Ровный дневной рост до total за days дней, последний замер — вчера. */
    private List<ApplicationViewSnapshot> steadyGrowth(Application application, long total, int days) {
        List<ApplicationViewSnapshot> snapshots = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            snapshots.add(snapshot(application, NOW.minus(Duration.ofDays(days - day + 1)), total * day / days));
        }
        return snapshots;
    }

    private static ApplicationViewSnapshot snapshot(Application application, Instant capturedAt, long views) {
        return ApplicationViewSnapshot.builder()
                .application(application)
                .capturedAt(capturedAt)
                .views(views)
                .source(ViewSource.YOUTUBE_API)
                .build();
    }

    private static ApplicationViewSnapshot last(List<ApplicationViewSnapshot> snapshots) {
        return snapshots.get(snapshots.size() - 1);
    }
}
