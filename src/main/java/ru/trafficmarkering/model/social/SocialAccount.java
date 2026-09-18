package ru.trafficmarkering.model.social;

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
import ru.trafficmarkering.model.application.Platform;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "social_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class SocialAccount {

    public static final String YOUTUBE_ANALYTICS_SCOPE = "https://www.googleapis.com/auth/yt-analytics.readonly";

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Platform platform;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    private String username;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "avatar_url", length = 1024)
    private String avatarUrl;

    private Long followers;

    @ToString.Exclude
    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @ToString.Exclude
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(length = 512)
    private String scopes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SocialAccountStatus status;

    @CreationTimestamp
    @Column(name = "connected_at", updatable = false)
    private Instant connectedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    public boolean grants(String scope) {
        return scopes != null && Arrays.asList(scopes.trim().split("[\\s,]+")).contains(scope);
    }

    public boolean reportsViewGeography() {
        return platform == Platform.YOUTUBE_SHORTS && grants(YOUTUBE_ANALYTICS_SCOPE);
    }
}
