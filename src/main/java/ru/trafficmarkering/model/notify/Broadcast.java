package ru.trafficmarkering.model.notify;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "broadcast")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Broadcast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BroadcastAudience audience;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BroadcastChannel channel;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "button_text", length = 64)
    private String buttonText;

    @Column(name = "button_url", length = 512)
    private String buttonUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BroadcastStatus status;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int sent;

    @Column(nullable = false)
    private int failed;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
