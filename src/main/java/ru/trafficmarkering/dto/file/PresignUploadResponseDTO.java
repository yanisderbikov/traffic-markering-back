package ru.trafficmarkering.dto.file;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ссылка на прямую загрузку файла в хранилище")
public record PresignUploadResponseDTO(
        @Schema(description = "Куда отправить PUT с телом файла и тем же Content-Type") String uploadUrl,
        @Schema(description = "Ключ файла — его передают при сохранении объявления") String key
) {}
