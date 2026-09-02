package ru.trafficmarkering.model.campaign;

/**
 * Жизненный цикл объявления. На публичной доске показываются только ACTIVE:
 * DRAFT ещё не готов, PAUSED заказчик временно снял, COMPLETED отработал своё.
 */
public enum CampaignStatus {
    DRAFT("Черновик"),
    ACTIVE("Активно"),
    PAUSED("На паузе"),
    COMPLETED("Завершено");

    private final String description;

    CampaignStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
