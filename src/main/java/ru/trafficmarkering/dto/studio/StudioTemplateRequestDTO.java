package ru.trafficmarkering.dto.studio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.trafficmarkering.service.studio.StudioSettings;

import java.util.UUID;

public record StudioTemplateRequestDTO(@NotBlank @Size(max = 80) String name,
                                       @NotNull StudioSettings settings,
                                       String captions,
                                       UUID bannerAssetId,
                                       UUID backgroundAssetId) {}
