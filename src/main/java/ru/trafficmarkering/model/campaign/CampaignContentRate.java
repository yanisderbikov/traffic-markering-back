package ru.trafficmarkering.model.campaign;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Тематика ролика со своей ставкой: заказчик выбирает несколько тематик из справочника и ставит
 * ставку за каждую, креатор при подаче выбирает, к какой относится его ролик.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
public class CampaignContentRate {

    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_COUNT = 20;

    @Column(name = "name", nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    /** Ставка за 1000 просмотров, в копейках */
    @Column(name = "rate_per_thousand_kopecks", nullable = false)
    private Long ratePerThousandKopecks;

    /** Код тематики из справочника; null — название задано от руки, так было до выбора из справочника */
    @Column(name = "topic_code", length = 32)
    private String topicCode;

    public CampaignContentRate(String name, Long ratePerThousandKopecks) {
        this(name, ratePerThousandKopecks, null);
    }
}
