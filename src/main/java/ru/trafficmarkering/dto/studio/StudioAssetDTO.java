package ru.trafficmarkering.dto.studio;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

public record StudioAssetDTO(UUID id, String kind, String name, boolean shared, boolean own, boolean video,
                             long sizeBytes, String url, @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt) {}
