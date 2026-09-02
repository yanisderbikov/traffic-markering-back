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

import java.time.Instant;
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
    @Column(nullable = false, length = 32)
    private Platform platform;

    @Column(name = "video_url", nullable = false, length = 1024)
    private String videoUrl;

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

    /** Когда просмотры обновлялись в последний раз; null — ещё ни разу */
    @Column(name = "views_synced_at")
    private Instant viewsSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Начисления идут только по одобренным откликам — на них завязан пересчёт бюджета. */
    public boolean isAccruable() {
        return status == ApplicationStatus.APPROVED || status == ApplicationStatus.COMPLETED;
    }
}
