package ru.trafficmarkering.model.campaign;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;
import ru.trafficmarkering.model.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Объявление на рекламную интеграцию: ставка за 1000 просмотров и выделенный бюджет.
 * spentKopecks — не «вручную списанное», а всегда сумма начислений по одобренным
 * откликам: его целиком пересчитывает CampaignAccrualService.
 */
@Entity
@Table(name = "campaign")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Campaign {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Короткий номер для публичных ссылок: внутренний UUID наружу не отдаём */
    @Column(name = "public_id", nullable = false, unique = true, length = 16)
    private String publicId;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "photo_key", length = 512)
    private String photoKey;

    /** Ставка за 1000 просмотров, в копейках */
    @Column(name = "rate_per_thousand_kopecks", nullable = false)
    private Long ratePerThousandKopecks;

    /** Выделенный бюджет, в копейках */
    @Column(name = "budget_kopecks", nullable = false)
    private Long budgetKopecks;

    /** Уже начислено криаторам, в копейках */
    @Builder.Default
    @Column(name = "spent_kopecks", nullable = false)
    private Long spentKopecks = 0L;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Сколько бюджета ещё можно раздать; в минус не уходит даже при ручной правке сумм. */
    public long remainingKopecks() {
        long budget = budgetKopecks != null ? budgetKopecks : 0L;
        long spent = spentKopecks != null ? spentKopecks : 0L;
        return Math.max(0L, budget - spent);
    }
}
