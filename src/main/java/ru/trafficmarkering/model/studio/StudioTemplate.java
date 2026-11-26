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
@Table(name = "studio_template")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudioTemplate {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String settings;

    @Column(columnDefinition = "TEXT")
    private String captions;

    @Column(name = "banner_asset_id")
    private UUID bannerAssetId;

    @Column(name = "background_asset_id")
    private UUID backgroundAssetId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
