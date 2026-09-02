package ru.trafficmarkering.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.trafficmarkering.model.application.Platform;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Отклик криатора на объявление")
public class ApplicationCreateRequestDTO {

    @NotNull(message = "Объявление обязательно")
    @Schema(description = "ID объявления", required = true)
    private UUID campaignId;

    @NotNull(message = "Площадка обязательна")
    @Schema(description = "Площадка: TELEGRAM, INSTAGRAM, TIKTOK или YOUTUBE_SHORTS",
            required = true, example = "TIKTOK")
    private Platform platform;

    @NotBlank(message = "Ссылка на ролик обязательна")
    @Size(max = 1024, message = "Ссылка не длиннее 1024 символов")
    @Schema(description = "Ссылка на выложенный ролик", required = true, example = "https://www.tiktok.com/@demo/video/123")
    private String videoUrl;

    @Schema(description = "Комментарий заказчику: что сняли и почему так")
    private String comment;
}
