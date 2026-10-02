package ru.trafficmarkering.model.fraud;

/**
 * Вердикт антифрода по отклику. CLEAN и SUSPICIOUS/FRAUD выставляет скоринг после каждого
 * замера просмотров, VERIFIED и FRAUD с отметкой о ревью — решение админа, и его
 * автоматика уже не перебивает.
 */
public enum FraudStatus {
    CLEAN("Чисто"),
    SUSPICIOUS("Подозрение на накрутку"),
    FRAUD("Накрутка"),
    VERIFIED("Проверен вручную");

    private final String description;

    FraudStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** Деньги по отклику копятся, но в кошелёк не уезжают, пока не разберутся. */
    public boolean blocksCredit() {
        return this == SUSPICIOUS || this == FRAUD;
    }

    /** По подтверждённой или автоматически распознанной накрутке начисления обнуляются. */
    public boolean blocksAccrual() {
        return this == FRAUD;
    }

    public boolean needsAttention() {
        return this == SUSPICIOUS || this == FRAUD;
    }
}
