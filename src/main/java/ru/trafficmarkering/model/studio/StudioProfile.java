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
@Table(name = "studio_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudioProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "active_template_id")
    private UUID activeTemplateId;

    @Column(name = "favorite_template_id")
    private UUID favoriteTemplateId;

    @Builder.Default
    @Column(name = "preset_queue", nullable = false, columnDefinition = "TEXT")
    private String presetQueue = "";

    @Column(name = "telegram_id", unique = true)
    private Long telegramId;

    @Column(name = "telegram_username", length = 64)
    private String telegramUsername;

    @Column(name = "telegram_linked_at")
    private Instant telegramLinkedAt;

    @Column(name = "link_code", length = 48, unique = true)
    private String linkCode;

    @Column(name = "link_code_expires_at")
    private Instant linkCodeExpiresAt;
}
