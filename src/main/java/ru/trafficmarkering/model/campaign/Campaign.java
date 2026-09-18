package ru.trafficmarkering.model.campaign;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Platform;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    @Column(name = "min_payout_kopecks", nullable = false)
    private Long minPayoutKopecks;

    @Column(name = "min_video_seconds")
    private Integer minVideoSeconds;

    @Column(name = "min_paid_views")
    private Long minPaidViews;

    @Column(name = "max_videos_per_creator")
    private Integer maxVideosPerCreator;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    /** Уже начислено криаторам, в копейках */
    @Builder.Default
    @Column(name = "spent_kopecks", nullable = false)
    private Long spentKopecks = 0L;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "view_region", nullable = false, length = 32)
    private ViewRegion viewRegion = ViewRegion.WORLD;

    @Builder.Default
    @ToString.Exclude
    @ElementCollection
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "campaign_platform", joinColumns = @JoinColumn(name = "campaign_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 32)
    private Set<Platform> platforms = new HashSet<>();

    @Builder.Default
    @ToString.Exclude
    @ElementCollection
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "campaign_material", joinColumns = @JoinColumn(name = "campaign_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<CampaignMaterial> materials = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public boolean acceptsPlatform(Platform platform) {
        return platforms != null && platforms.contains(platform);
    }

    public long minPayout() {
        return minPayoutKopecks != null ? minPayoutKopecks : 0L;
    }

    public boolean startedBy(Instant now) {
        return startsAt == null || !startsAt.isAfter(now);
    }

    public boolean endedBy(Instant now) {
        return endsAt != null && endsAt.isBefore(now);
    }

    public boolean acceptsApplicationsAt(Instant now) {
        return status == CampaignStatus.ACTIVE && startedBy(now) && !endedBy(now);
    }

    public boolean paysViews(long views) {
        return minPaidViews == null || views >= minPaidViews;
    }

    public ViewRegion viewRegion() {
        return viewRegion != null ? viewRegion : ViewRegion.WORLD;
    }

    public boolean limitsVideosPerCreator() {
        return maxVideosPerCreator != null;
    }

    /** Сколько бюджета ещё можно раздать; в минус не уходит даже при ручной правке сумм. */
    public long remainingKopecks() {
        long budget = budgetKopecks != null ? budgetKopecks : 0L;
        long spent = spentKopecks != null ? spentKopecks : 0L;
        return Math.max(0L, budget - spent);
    }
}
