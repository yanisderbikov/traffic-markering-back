package ru.trafficmarkering.dto.studio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** purpose: SOURCE | BANNER | BACKGROUND */
public record StudioPresignRequestDTO(@NotBlank @Size(max = 160) String filename,
                                      @NotBlank @Size(max = 128) String contentType,
                                      @NotBlank String purpose,
                                      @NotNull @Positive Long size) {}
