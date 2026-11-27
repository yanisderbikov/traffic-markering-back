package ru.trafficmarkering.model.moderation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.trafficmarkering.model.application.ApplicationStatus;

import java.time.Instant;
import java.util.UUID;

/** Заявка на модерации у конкретного менеджера: одна активная на менеджера и на ролик */
@Entity
@Table(name = "moderation_assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationAssignment {

    public enum Status {
        /** У менеджера, ждёт решения */
        ACTIVE,
        /** Менеджер принял или отклонил */
        DECIDED,
        /** Время вышло — заявка ушла следующему */
        EXPIRED,
        /** Ролик решили мимо бота (на сайте) или удалили, либо менеджера убрали из списка */
        CANCELLED,
        /** Не удалось доставить в Telegram */
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** null — отклик удалён; publicId остаётся для истории */
    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "application_public_id", nullable = false, length = 16)
    private String applicationPublicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderator_id", nullable = false)
    private Moderator moderator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ApplicationStatus decision;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Builder.Default
    @Column(name = "awaiting_reason", nullable = false)
    private boolean awaitingReason = false;

    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "with_video")
    private Boolean withVideo;

    @Column(name = "video_file_id", length = 256)
    private String videoFileId;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    public boolean active() {
        return status == Status.ACTIVE;
    }

    public void close(Status status, Instant now) {
        this.status = status;
        this.closedAt = now;
        this.awaitingReason = false;
    }
}
