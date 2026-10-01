package ru.trafficmarkering.model.campaign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Locale;

/**
 * Тематика объявления. Базовые заведены миграцией вместе со средней ставкой за 1000 просмотров
 * на рынке UGC-роликов: ставка ниже средней — креаторы берут такие задачи неохотно, заказчику
 * об этом говорим в мастере. Остальные добавляют заказчики, если подходящей не нашлось, —
 * средней ставки у них нет.
 */
@Entity
@Table(name = "campaign_topic")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CampaignTopic {

    /** Строковый ключ: у базовых — бывшее имя enum (TECH), у добавленных — случайный короткий номер */
    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    /** Ключ поиска и защиты от дублей, см. {@link #normalize(String)} */
    @Column(name = "normalized_name", nullable = false, unique = true, length = 64)
    private String normalizedName;

    /** Средняя ставка за 1000 просмотров, в копейках; null — неизвестна */
    @Column(name = "average_rate_per_thousand_kopecks")
    private Long averageRatePerThousandKopecks;

    /** Кто добавил; null — базовая тематика */
    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /** Регистр, «ё» и лишние пробелы не различаем: «Ёлки  и игрушки» и «елки и игрушки» — одна тематика. */
    public static String normalize(String name) {
        return name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).replace('ё', 'е');
    }
}
