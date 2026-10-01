package ru.trafficmarkering.model.application;

/**
 * Судьба отклика криатора. Начисления идут только по APPROVED и COMPLETED:
 * IN_PROGRESS — оффер взят в работу, ролика ещё нет; PENDING ждёт модерации менеджером или заказчиком,
 * REJECTED отклонён на модерации.
 */
public enum ApplicationStatus {
    IN_PROGRESS("В работе"),
    PENDING("На модерации"),
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
