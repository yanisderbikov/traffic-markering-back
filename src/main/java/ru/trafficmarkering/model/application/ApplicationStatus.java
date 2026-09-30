package ru.trafficmarkering.model.application;

/**
 * Судьба отклика криатора. Начисления идут только по APPROVED и COMPLETED:
 * IN_PROGRESS — оффер взят в работу, ролика ещё нет; PENDING ещё не одобрен, REJECTED заказчик отклонил.
 */
public enum ApplicationStatus {
    IN_PROGRESS("В работе"),
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
