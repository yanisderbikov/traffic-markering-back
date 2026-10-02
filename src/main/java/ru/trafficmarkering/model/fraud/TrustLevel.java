package ru.trafficmarkering.model.fraud;

/**
 * Репутация криатора. Новичок работает с потолком оплачиваемых просмотров на ролик,
 * проверенный — без ограничений, ограниченный получает деньги только после ручной
 * проверки каждого ролика, заблокированный не может откликаться и ничего не получает.
 */
public enum TrustLevel {
    NEW("Новичок"),
    TRUSTED("Проверенный"),
    RESTRICTED("Ограничен"),
    BLOCKED("Заблокирован");

    private final String description;

    TrustLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** Оплачиваемые просмотры одного ролика режутся потолком из настроек. */
    public boolean capsPayableViews() {
        return this == NEW || this == RESTRICTED;
    }

    /** Ночное зачисление ждёт статуса VERIFIED по каждому отклику. */
    public boolean requiresManualVerification() {
        return this == RESTRICTED;
    }

    public boolean blocksCredit() {
        return this == BLOCKED;
    }

    public boolean blocksApplications() {
        return this == BLOCKED;
    }
}
