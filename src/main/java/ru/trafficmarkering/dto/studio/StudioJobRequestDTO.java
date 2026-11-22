package ru.trafficmarkering.dto.studio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.trafficmarkering.service.studio.StudioSettings;

import java.util.UUID;

/**
 * Новая задача с сайта. Исходник уже загружен в хранилище по presigned-ссылке.
 * Баннер и фон — либо свежая загрузка (key), либо файл из библиотеки (assetId).
 */
public record StudioJobRequestDTO(@NotBlank String sourceKey,
                                  @NotBlank @Size(max = 160) String filename,
                                  @NotNull StudioSettings settings,
                                  String captions,
                                  String bannerKey,
                                  UUID bannerAssetId,
                                  String backgroundKey,
                                  UUID backgroundAssetId) {}
