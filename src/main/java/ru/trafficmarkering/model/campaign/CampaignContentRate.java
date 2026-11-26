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
 * Тематика ролика со своей ставкой: заказчик называет её сам («CS и Dota 2», «Юмор»),
 * криатор при подаче выбирает, к какой относится его ролик.
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
}
