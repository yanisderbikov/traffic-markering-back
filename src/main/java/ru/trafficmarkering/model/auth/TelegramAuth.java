package ru.trafficmarkering.model.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.trafficmarkering.model.Role;

import java.time.Instant;

@Entity
@Table(name = "telegram_auth")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramAuth {

    /** Публичный код: уходит в ссылку t.me и в кнопки бота */
    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "secret_hash", nullable = false, unique = true, length = 64)
    private String secretHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private TelegramAuthOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TelegramAuthStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Role role;

    @Column(name = "referral_code", length = 64)
    private String referralCode;

    @Column(name = "telegram_id")
    private Long telegramId;

    @Column(name = "telegram_username", length = 64)
    private String telegramUsername;

    @Column(name = "telegram_name")
    private String telegramName;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "client_info")
    private String clientInfo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public boolean expired() {
        return expiresAt.isBefore(Instant.now());
    }
}
