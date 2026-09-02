package ru.trafficmarkering.dto.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос ссылки на прямую загрузку файла в хранилище")
public class PresignUploadRequestDTO {

    @NotBlank(message = "Имя файла обязательно")
    @Size(max = 255, message = "Имя файла не длиннее 255 символов")
    @Schema(description = "Имя файла — из него берётся только расширение", required = true, example = "photo.jpg")
    private String filename;

    @NotBlank(message = "Тип файла обязателен")
    @Schema(description = "MIME-тип файла, допускаются только изображения", required = true, example = "image/jpeg")
    private String contentType;
}
