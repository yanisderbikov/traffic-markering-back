package ru.trafficmarkering.model.studio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "studio_chat_draft")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudioChatDraft {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "file_id", nullable = false, length = 256)
    private String fileId;

    @Column(nullable = false, length = 160)
    private String filename;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
