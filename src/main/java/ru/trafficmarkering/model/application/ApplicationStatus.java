package ru.trafficmarkering.model.application;

/**
 * Судьба отклика криатора. Начисления идут только по APPROVED и COMPLETED:
 * PENDING ещё не одобрен, REJECTED заказчик отклонил.
 */
public enum ApplicationStatus {
    PENDING("На рассмотрении"),
    APPROVED("Одобрен"),
    REJECTED("Отклонён"),
    COMPLETED("Завершён");

    private final String description;

    ApplicationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
