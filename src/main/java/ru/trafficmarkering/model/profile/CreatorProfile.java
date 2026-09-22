package ru.trafficmarkering.model.profile;

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
import ru.trafficmarkering.model.fraud.TrustLevel;

import java.time.Instant;
import java.util.UUID;

/**
 * Профиль криатора. Заводится пустым сразу при регистрации, чтобы не городить
 * «профиль ещё не создан» в каждом сервисе. Ссылки на площадки видит заказчик:
 * по ним он решает, брать ли отклик.
 */
@Entity
@Table(name = "creator_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CreatorProfile {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ToString.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Псевдоним, под которым криатор показывается заказчику; пусто — берём имя из учётки */
    @Column(name = "display_name")
    private String displayName;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    private String telegram;

    private String instagram;

    private String tiktok;

    @Column(name = "youtube_shorts")
    private String youtubeShorts;

    /** Репутация: новичок, проверенный, ограничен, заблокирован */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "trust_level", nullable = false, length = 16)
    private TrustLevel trustLevel = TrustLevel.NEW;

    /** Уровень выставил админ руками — автоматика его не пересчитывает */
    @Builder.Default
    @Column(name = "trust_manual", nullable = false)
    private Boolean trustManual = Boolean.FALSE;

    @Column(name = "trust_note", columnDefinition = "TEXT")
    private String trustNote;

    @Column(name = "trust_updated_at")
    private Instant trustUpdatedAt;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trust_updated_by")
    private User trustUpdatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public TrustLevel trustLevel() {
        return trustLevel != null ? trustLevel : TrustLevel.NEW;
    }

    public boolean isTrustManual() {
        return Boolean.TRUE.equals(trustManual);
    }
}
