package ru.trafficmarkering.dto.admin;

import ru.trafficmarkering.repository.PlatformStats;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Дашборд супер-админа. Каждая метрика — значение за всё время и прирост за последние periodDays дней.
 * Суммы в копейках.
 */
public record PlatformDashboardDTO(
        int periodDays,
        String generatedAt,
        BigDecimal commissionPercent,
        Metric creators,
        Metric customers,
        Metric publishedVideos,
        long views,
        long activeCampaigns,
        Metric adTurnoverKopecks,
        Metric topUpsKopecks,
        Metric commissionKopecks,
        Metric partnerRewardsKopecks,
        Metric platformIncomeKopecks
) {
    public record Metric(long total, long period) {
    }

    public static PlatformDashboardDTO from(PlatformStats stats, int periodDays, Instant generatedAt,
                                            BigDecimal commissionPercent) {
        return new PlatformDashboardDTO(
                periodDays,
                generatedAt.toString(),
                commissionPercent,
                new Metric(stats.creators(), stats.creatorsSince()),
                new Metric(stats.customers(), stats.customersSince()),
                new Metric(stats.publishedVideos(), stats.publishedVideosSince()),
                stats.views(),
                stats.activeCampaigns(),
                new Metric(stats.adTurnoverKopecks(), stats.adTurnoverSinceKopecks()),
                new Metric(stats.topUpsKopecks(), stats.topUpsSinceKopecks()),
                new Metric(stats.commissionKopecks(), stats.commissionSinceKopecks()),
                new Metric(stats.partnerRewardsKopecks(), stats.partnerRewardsSinceKopecks()),
                new Metric(stats.commissionKopecks() - stats.partnerRewardsKopecks(),
                        stats.commissionSinceKopecks() - stats.partnerRewardsSinceKopecks()));
    }
}
