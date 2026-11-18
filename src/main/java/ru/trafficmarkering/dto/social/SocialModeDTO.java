package ru.trafficmarkering.dto.social;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Как сейчас привязываются аккаунты соцсетей")
public record SocialModeDTO(
        @Schema(description = "OAUTH — вход на площадке (/authorize), SCRAPE — ник и код в описании профиля (/verification)",
                example = "SCRAPE") String mode,
        @Schema(description = "Привязка настроена на сервере; false — ручки отвечают 503") boolean available
) {
}
