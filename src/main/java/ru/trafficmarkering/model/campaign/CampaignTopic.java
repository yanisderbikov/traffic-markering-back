package ru.trafficmarkering.model.campaign;

/**
 * Тематика объявления и средняя ставка за 1000 просмотров по ней на рынке UGC-роликов.
 * Ставка ниже средней — креаторы берут такие задачи неохотно; заказчику об этом говорим в мастере.
 */
public enum CampaignTopic {
    ENTERTAINMENT("Развлечения и юмор", 80_00L),
    GAMING("Игры", 100_00L),
    LIFESTYLE("Лайфстайл", 120_00L),
    FOOD("Еда и рестораны", 120_00L),
    BEAUTY_FASHION("Красота и мода", 150_00L),
    SPORT_HEALTH("Спорт и здоровье", 150_00L),
    TRAVEL("Путешествия", 150_00L),
    APPS("Приложения и сервисы", 180_00L),
    TECH("Технологии и гаджеты", 200_00L),
    EDUCATION("Образование", 200_00L),
    AUTO("Авто", 220_00L),
    REAL_ESTATE("Недвижимость", 300_00L),
    FINANCE("Финансы и инвестиции", 350_00L),
    OTHER("Другое", 150_00L);

    private final String description;
    private final long averageRatePerThousandKopecks;

    CampaignTopic(String description, long averageRatePerThousandKopecks) {
        this.description = description;
        this.averageRatePerThousandKopecks = averageRatePerThousandKopecks;
    }

    public String getDescription() {
        return description;
    }

    public long getAverageRatePerThousandKopecks() {
        return averageRatePerThousandKopecks;
    }
}
