package ru.trafficmarkering.repository;

/**
 * Сводные цифры площадки для дашборда супер-админа. Поля *Since — то же самое,
 * но только за период, начало которого передали в {@link GetterPlatformStats#getStats}.
 * Суммы в копейках.
 */
public record PlatformStats(long creators,
                            long creatorsSince,
                            long customers,
                            long customersSince,
                            long publishedVideos,
                            long publishedVideosSince,
                            long views,
                            long activeCampaigns,
                            long adTurnoverKopecks,
                            long adTurnoverSinceKopecks,
                            long topUpsKopecks,
                            long topUpsSinceKopecks,
                            long commissionKopecks,
                            long commissionSinceKopecks,
                            long partnerRewardsKopecks,
                            long partnerRewardsSinceKopecks) {
}
