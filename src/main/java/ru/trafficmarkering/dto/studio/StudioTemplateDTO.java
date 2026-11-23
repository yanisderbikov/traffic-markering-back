package ru.trafficmarkering.dto.studio;

import ru.trafficmarkering.service.studio.StudioSettings;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

public record StudioTemplateDTO(UUID id, String name, StudioSettings settings, String captions,
                                UUID bannerAssetId, String bannerName, UUID backgroundAssetId, String backgroundName,
                                boolean active, boolean favorite, @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt) {}
