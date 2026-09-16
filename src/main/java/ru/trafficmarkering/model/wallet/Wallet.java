package ru.trafficmarkering.model.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;
import ru.trafficmarkering.model.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Денежный счёт пользователя. Баланс хранится в копейках и не может быть отрицательным.
 * За происхождение денег отвечает журнал WalletTransaction, поэтому прямые изменения
 * balanceKopecks за пределами wallet-сервиса не допускаются.
 */
@Entity
@Table(name = "wallet")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Wallet {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ToString.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    @Column(name = "balance_kopecks", nullable = false)
    private long balanceKopecks = 0L;

    /** Optimistic version is a second line of defence in addition to the write lock. */
    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Applies a signed delta. Positive value credits the wallet, negative value debits it.
     * The domain object itself protects the invariant even if another caller appears later.
     */
    public void adjust(long amountKopecks) {
        if (amountKopecks == 0L) {
            throw new IllegalArgumentException("Сумма изменения не может быть нулевой");
        }

        long nextBalance = Math.addExact(balanceKopecks, amountKopecks);
        if (nextBalance < 0L) {
            throw new IllegalArgumentException("Баланс кошелька не может быть отрицательным");
        }
        balanceKopecks = nextBalance;
    }
}
