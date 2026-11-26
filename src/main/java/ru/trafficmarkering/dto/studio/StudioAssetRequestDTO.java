package ru.trafficmarkering.dto.studio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Сохранить загруженный файл в библиотеку. kind: BANNER | BACKGROUND; shared — общий каталог (только админ) */
public record StudioAssetRequestDTO(@NotBlank String key, @NotBlank String kind,
                                    @NotBlank @Size(max = 120) String name, boolean shared) {}
