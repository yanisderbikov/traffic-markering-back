package ru.trafficmarkering.repository;

/**
 * Другие запущенные объявления той же тематики: сколько их, медианы ставки и бюджета
 * и у скольких из них значение ниже, чем у сравниваемого объявления.
 *
 * @param medianRatePerThousandKopecks null — сравнивать не с чем
 * @param medianBudgetKopecks          null — сравнивать не с чем
 */
public record CampaignSegmentStats(long campaigns,
                                   Long medianRatePerThousandKopecks,
                                   Long medianBudgetKopecks,
                                   long lowerRate,
                                   long lowerBudget) {
}
