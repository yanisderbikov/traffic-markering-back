package ru.trafficmarkering.dto.studio;

import java.time.Instant;
import java.util.UUID;

public record StudioAssetDTO(UUID id, String kind, String name, boolean shared, boolean own, boolean video,
                             long sizeBytes, String url, Instant createdAt) {}
