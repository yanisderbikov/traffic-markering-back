package ru.trafficmarkering.model.wallet;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.trafficmarkering.model.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Entity
@Table(name = "transfer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Transfer {

    public static final Pattern TRON_ADDRESS = Pattern.compile("^T[1-9A-HJ-NP-Za-km-z]{33}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private WalletTransaction transaction;

    @Column(name = "tron_address", length = 64)
    private String tronAddress;

    @Column(name = "tx_id", length = 255)
    private String txId;

    @Column(name = "finance_comment", columnDefinition = "TEXT")
    private String financeComment;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @Builder.Default
    @ToString.Exclude
    @ElementCollection
    @CollectionTable(name = "transfer_proof", joinColumns = @JoinColumn(name = "transfer_id"))
    @OrderColumn(name = "position")
    @Column(name = "file_key", nullable = false, length = 512)
    private List<String> proofKeys = new ArrayList<>();

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static boolean isTronAddress(String value) {
        return value != null && TRON_ADDRESS.matcher(value).matches();
    }

    public void send(User actor, String txId, List<String> proofKeys, String comment) {
        this.proofKeys.clear();
        this.proofKeys.addAll(proofKeys);
        this.txId = txId;
        this.financeComment = comment;
        this.processedBy = actor;
        this.sentAt = Instant.now();
    }

    public void confirm() {
        Instant now = Instant.now();
        confirmedAt = now;
        closedAt = now;
    }

    public void reject(User actor, String reason) {
        rejectReason = reason;
        processedBy = actor;
        closedAt = Instant.now();
    }

    public void cancel() {
        closedAt = Instant.now();
    }
}
