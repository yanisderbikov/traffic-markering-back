package ru.trafficmarkering.model.wallet;

public enum WalletTransactionType {
    TOP_UP("Пополнение"),
    WITHDRAWAL("Вывод USDT (TRC-20)"),
    ALLOCATION("Резерв под объявление"),
    RELEASE("Возврат из объявления"),
    EARNING("Начисление за просмотры"),
    PAYOUT("Выплата USDT (TRC-20)"),
    REFERRAL_REWARD("Партнёрское вознаграждение");

    private final String description;

    WalletTransactionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isExternal() {
        return this == TOP_UP || this == WITHDRAWAL || this == PAYOUT;
    }

    public boolean settlesOnConfirm() {
        return this == TOP_UP;
    }

    public boolean chargesCommissionOnTop() {
        return this == TOP_UP;
    }
}
