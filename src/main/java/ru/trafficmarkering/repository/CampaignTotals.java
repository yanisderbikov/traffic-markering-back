package ru.trafficmarkering.repository;

public record CampaignTotals(Long customerId, Long budgetKopecks, Long spentKopecks) {

    public long budget() {
        return budgetKopecks != null ? budgetKopecks : 0L;
    }

    public long spent() {
        return spentKopecks != null ? spentKopecks : 0L;
    }
}
