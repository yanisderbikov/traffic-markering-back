package ru.trafficmarkering.dto.placement;

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
@Schema(description = "Ролик для расчёта работы «ссылка и промокод»")
public class SnapshotQuoteRequestDTO {

    @NotBlank(message = "Ссылка на ролик обязательна")
    @Size(max = 1024, message = "Ссылка не длиннее 1024 символов")
    @Schema(description = "Ссылка на выложенный ролик: YouTube, TikTok или Instagram", required = true,
            example = "https://www.tiktok.com/@demo/video/123")
    private String videoUrl;
}
