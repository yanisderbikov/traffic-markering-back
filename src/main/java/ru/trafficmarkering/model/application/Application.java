package ru.trafficmarkering.model.application;

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
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.ViewRegion;
import ru.trafficmarkering.model.fraud.FraudFlag;
import ru.trafficmarkering.model.fraud.FraudFlagsConverter;
import ru.trafficmarkering.model.fraud.FraudStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Отклик криатора на объявление: одна площадка, один ролик.
 * accruedKopecks считает платформа (CampaignAccrualService), руками его не правят:
 * иначе сумма начислений разъедется с campaign.spentKopecks.
 */
@Entity
@Table(name = "application")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Application {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Version
    private Long version;

    @Column(name = "public_id", nullable = false, unique = true, length = 16)
    private String publicId;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Platform platform;

    @Column(name = "video_url", length = 1024)
    private String videoUrl;

    @Column(name = "video_key", length = 512)
    private String videoKey;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    /** Просмотры ролика: их проставляет внешний анализатор, сами мы их не считаем */
    @Builder.Default
    @Column(nullable = false)
    private Long views = 0L;

    /** Начислено криатору за эти просмотры, в копейках */
    @Builder.Default
    @Column(name = "accrued_kopecks", nullable = false)
    private Long accruedKopecks = 0L;

    @Builder.Default
    @Column(name = "credited_kopecks", nullable = false)
    private Long creditedKopecks = 0L;

    @Convert(converter = CountryViewsConverter.class)
    @Column(name = "country_views", columnDefinition = "TEXT")
    private Map<String, Long> countryViews;

    /** Когда просмотры обновлялись в последний раз; null — ещё ни разу */
    @Column(name = "views_synced_at")
    private Instant viewsSyncedAt;

    /** Когда ролик опубликован на площадке; null — площадка не отдала или ещё не синкали */
    @Column(name = "video_published_at")
    private Instant videoPublishedAt;

    /** Вердикт антифрода: выставляет скоринг после замера или админ вручную */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "fraud_status", nullable = false, length = 32)
    private FraudStatus fraudStatus = FraudStatus.CLEAN;

    /** Сумма баллов сработавших правил, 0–100 */
    @Builder.Default
    @Column(name = "fraud_score", nullable = false)
    private Integer fraudScore = 0;

    @Builder.Default
    @Convert(converter = FraudFlagsConverter.class)
    @Column(name = "fraud_flags", columnDefinition = "TEXT")
    private List<FraudFlag> fraudFlags = List.of();

    @Column(name = "fraud_checked_at")
    private Instant fraudCheckedAt;

    /** Отметка ручного решения: пока она стоит, автоматический скоринг статус не меняет */
    @Column(name = "fraud_reviewed_at")
    private Instant fraudReviewedAt;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fraud_reviewed_by")
    private User fraudReviewedBy;

    @Column(name = "fraud_review_comment", columnDefinition = "TEXT")
    private String fraudReviewComment;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderated_by")
    private User moderatedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // ---- «Ссылка и промокод» (Campaign.paymentModel = SNAPSHOT) ----

    /** Базовая ставка за 1000 просмотров на момент, когда криатор взял оффер, в копейках */
    @Column(name = "base_rate_kopecks")
    private Long baseRateKopecks;

    /** Надбавка за ссылку в профиле на момент взятия оффера, в копейках за 1000 просмотров */
    @Column(name = "link_bonus_kopecks")
    private Long linkBonusKopecks;

    /** Надбавка за промокод в закрепе на момент взятия оффера, в копейках за 1000 просмотров */
    @Column(name = "promo_bonus_kopecks")
    private Long promoBonusKopecks;

    /** Замер просмотров, по которому посчитана работа: снят при подаче, дальше не меняется */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_snapshot_id")
    private ApplicationViewSnapshot billingSnapshot;

    /** Оплачиваемые просмотры работы: из снимка при подаче, с учётом региона, округления и потолка новичка */
    @Column(name = "billing_views")
    private Long billingViews;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** До какого момента криатор держит ссылку и закреп; после него подтверждённые надбавки засчитываются */
    @Column(name = "placement_retain_until")
    private Instant placementRetainUntil;

    @Builder.Default
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "state", column = @Column(name = "link_state", nullable = false, length = 32)),
            @AttributeOverride(name = "proofKey", column = @Column(name = "link_proof_key", length = 512)),
            @AttributeOverride(name = "proofUrl", column = @Column(name = "link_proof_url", length = 1024)),
            @AttributeOverride(name = "checkState", column = @Column(name = "link_check_state", length = 32)),
            @AttributeOverride(name = "checkDetail", column = @Column(name = "link_check_detail", columnDefinition = "TEXT")),
            @AttributeOverride(name = "checkedAt", column = @Column(name = "link_checked_at")),
            @AttributeOverride(name = "note", column = @Column(name = "link_note", columnDefinition = "TEXT")),
            @AttributeOverride(name = "decidedAt", column = @Column(name = "link_decided_at")),
            @AttributeOverride(name = "decidedBy", column = @Column(name = "link_decided_by"))
    })
    private Placement linkPlacement = new Placement();

    @Builder.Default
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "state", column = @Column(name = "promo_state", nullable = false, length = 32)),
            @AttributeOverride(name = "proofKey", column = @Column(name = "promo_proof_key", length = 512)),
            @AttributeOverride(name = "proofUrl", column = @Column(name = "promo_proof_url", length = 1024)),
            @AttributeOverride(name = "checkState", column = @Column(name = "promo_check_state", length = 32)),
            @AttributeOverride(name = "checkDetail", column = @Column(name = "promo_check_detail", columnDefinition = "TEXT")),
            @AttributeOverride(name = "checkedAt", column = @Column(name = "promo_checked_at")),
            @AttributeOverride(name = "note", column = @Column(name = "promo_note", columnDefinition = "TEXT")),
            @AttributeOverride(name = "decidedAt", column = @Column(name = "promo_decided_at")),
            @AttributeOverride(name = "decidedBy", column = @Column(name = "promo_decided_by"))
    })
    private Placement promoPlacement = new Placement();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public long accrued() {
        return accruedKopecks != null ? accruedKopecks : 0L;
    }

    public long uncreditedKopecks() {
        long credited = creditedKopecks != null ? creditedKopecks : 0L;
        return accrued() - credited;
    }

    /**
     * Начисления идут только по одобренным откликам — на них завязан пересчёт бюджета.
     * Распознанная накрутка начисление тоже гасит: заказчик не должен платить за ботов.
     */
    public boolean isAccruable() {
        return (status == ApplicationStatus.APPROVED || status == ApplicationStatus.COMPLETED)
                && !fraudStatus().blocksAccrual();
    }

    public FraudStatus fraudStatus() {
        return fraudStatus != null ? fraudStatus : FraudStatus.CLEAN;
    }

    public int fraudScoreValue() {
        return fraudScore != null ? fraudScore : 0;
    }

    public List<FraudFlag> fraudFlags() {
        return fraudFlags != null ? fraudFlags : List.of();
    }

    /** Решение админа стоит выше автоматики: скоринг после него статус не трогает. */
    public boolean isFraudReviewed() {
        return fraudReviewedAt != null;
    }

    public long totalViews() {
        return views != null ? views : 0L;
    }

    public boolean geographyKnown() {
        return countryViews != null;
    }

    public Placement link() {
        if (linkPlacement == null) {
            linkPlacement = new Placement();
        }
        return linkPlacement;
    }

    public Placement promo() {
        if (promoPlacement == null) {
            promoPlacement = new Placement();
        }
        return promoPlacement;
    }

    public Placement placement(PlacementKind kind) {
        return kind == PlacementKind.LINK ? link() : promo();
    }

    /** Работа подана по снимку: просмотры и ставка зафиксированы, рост после подачи её не меняет. */
    public boolean submittedBySnapshot() {
        return submittedAt != null && billingViews != null;
    }

    public long billingViewsValue() {
        return billingViews != null ? billingViews : 0L;
    }

    /** Ставки отклика, зафиксированные при взятии оффера. */
    public RateParts rateParts() {
        return new RateParts(
                baseRateKopecks != null ? baseRateKopecks : 0L,
                linkBonusKopecks != null ? linkBonusKopecks : 0L,
                promoBonusKopecks != null ? promoBonusKopecks : 0L);
    }

    /** Ставка, под которую занят бюджет: база и надбавки, по которым ещё нет отказа. */
    public long reservedRate() {
        return rateParts().rate(link().state().reservesBudget(), promo().state().reservesBudget());
    }

    /** Ставка, которая уже уходит в кошелёк: база и засчитанные надбавки. */
    public long creditableRate() {
        return rateParts().rate(link().state().creditable(), promo().state().creditable());
    }

    public PayableViews payableViews(ViewRegion region) {
        if (region == null || region.isWorld()) {
            return PayableViews.of(totalViews());
        }
        if (!geographyKnown()) {
            return PayableViews.withoutGeography();
        }
        return PayableViews.of(Math.min(totalViews(), region.viewsWithin(countryViews)));
    }
}
