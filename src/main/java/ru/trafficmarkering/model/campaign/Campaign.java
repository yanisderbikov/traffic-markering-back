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
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /** Срок размещения ссылки и закрепа по умолчанию, дней после подачи ролика */
    public static final int DEFAULT_RETENTION_DAYS = 7;

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Version
    private Long version;

    /** Короткий номер для публичных ссылок: внутренний UUID наружу не отдаём */
    @Column(name = "public_id", nullable = false, unique = true, length = 16)
    private String publicId;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "photo_key", length = 512)
    private String photoKey;

    /** Ставка за 1000 просмотров, в копейках */
    @Column(name = "rate_per_thousand_kopecks")
    private Long ratePerThousandKopecks;

    /** Выделенный бюджет, в копейках */
    @Column(name = "budget_kopecks")
    private Long budgetKopecks;

    @Column(name = "min_payout_kopecks")
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

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic")
    private CampaignTopic topic;

    /** Как платим за просмотры; SNAPSHOT — механика «ссылка и промокод» */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_model", nullable = false, length = 32)
    private PaymentModel paymentModel = PaymentModel.CONTINUOUS;

    /** Общая рекламная ссылка кампании: одна на всех криаторов, ставится в ссылки профиля */
    @Column(name = "promo_url", length = 1024)
    private String promoUrl;

    /** Общий многоразовый промокод кампании: криатор пишет его в закреплённом комментарии */
    @Column(name = "promo_code", length = 64)
    private String promoCode;

    /** Что даёт промокод зрителю и на каких условиях — попадает в шаблон комментария */
    @Column(name = "promo_benefit", columnDefinition = "TEXT")
    private String promoBenefit;

    /** Надбавка к базовой ставке за ссылку в профиле, в копейках за 1000 просмотров */
    @Column(name = "link_bonus_per_thousand_kopecks")
    private Long linkBonusPerThousandKopecks;

    /** Надбавка к базовой ставке за промокод в закреплённом комментарии, в копейках за 1000 просмотров */
    @Column(name = "promo_bonus_per_thousand_kopecks")
    private Long promoBonusPerThousandKopecks;

    /** Сколько дней после подачи держать ссылку и закреп; null — по умолчанию */
    @Column(name = "placement_retention_days")
    private Integer placementRetentionDays;

    /** Потолок вознаграждения за один ролик, в копейках; null — без потолка */
    @Column(name = "max_reward_per_video_kopecks")
    private Long maxRewardPerVideoKopecks;

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

    public List<String> missingForLaunch() {
        Map<String, Boolean> filled = new LinkedHashMap<>();
        filled.put("название", title != null && !title.isBlank());
        filled.put("описание", description != null && !description.isBlank());
        filled.put("обложка", photoKey != null && !photoKey.isBlank());
        filled.put("тематика", topic != null);
        filled.put("площадки", platforms != null && !platforms.isEmpty());
        filled.put("ставка", ratePerThousandKopecks != null && ratePerThousandKopecks > 0);
        filled.put("бюджет", budgetKopecks != null && budgetKopecks >= 0);
        filled.put("порог вывода", minPayoutKopecks != null && minPayoutKopecks > 0);
        if (paysBySnapshot()) {
            filled.put("рекламная ссылка", linkBonus() == 0 || hasText(promoUrl));
            filled.put("промокод", promoBonus() == 0 || hasText(promoCode));
        }
        return filled.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .toList();
    }

    public String displayTitle() {
        return title != null && !title.isBlank() ? title : "Без названия";
    }

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

    public PaymentModel paymentModel() {
        return paymentModel != null ? paymentModel : PaymentModel.CONTINUOUS;
    }

    public boolean paysBySnapshot() {
        return paymentModel() == PaymentModel.SNAPSHOT;
    }

    public long linkBonus() {
        return linkBonusPerThousandKopecks != null ? linkBonusPerThousandKopecks : 0L;
    }

    public long promoBonus() {
        return promoBonusPerThousandKopecks != null ? promoBonusPerThousandKopecks : 0L;
    }

    /** Предлагает ли объявление надбавку за ссылку: есть сама ссылка и прибавка к ставке. */
    public boolean offersLink() {
        return paysBySnapshot() && linkBonus() > 0 && hasText(promoUrl);
    }

    public boolean offersPromo() {
        return paysBySnapshot() && promoBonus() > 0 && hasText(promoCode);
    }

    public int retentionDays() {
        return placementRetentionDays != null ? placementRetentionDays : DEFAULT_RETENTION_DAYS;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Сколько бюджета ещё можно раздать; в минус не уходит даже при ручной правке сумм. */
    public long remainingKopecks() {
        long budget = budgetKopecks != null ? budgetKopecks : 0L;
        long spent = spentKopecks != null ? spentKopecks : 0L;
        return Math.max(0L, budget - spent);
    }
}
