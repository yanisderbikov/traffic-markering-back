package ru.trafficmarkering.model.studio;

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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "studio_asset")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudioAsset {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StudioAssetKind kind;

    @Column(nullable = false)
    private boolean shared;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private boolean video;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
