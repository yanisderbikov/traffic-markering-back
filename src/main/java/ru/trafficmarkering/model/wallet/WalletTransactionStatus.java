package ru.trafficmarkering.model.wallet;

public enum WalletTransactionStatus {
    DONE("Проведена"),
    PENDING("Ожидает отправки"),
    SENT("Отправлена, ждёт подтверждения"),
    CONFIRMED("Подтверждена"),
    REJECTED("Отклонена"),
    CANCELLED("Отменена");

    private final String description;

    WalletTransactionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean countsTowardBalance() {
        return this != REJECTED && this != CANCELLED;
    }

    public boolean isOpen() {
        return this == PENDING || this == SENT;
    }
}
