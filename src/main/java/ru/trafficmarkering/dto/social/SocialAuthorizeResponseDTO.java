package ru.trafficmarkering.dto.social;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ссылка, по которой криатора отправляют на страницу согласия площадки")
public record SocialAuthorizeResponseDTO(
        @Schema(description = "Площадка", example = "TIKTOK") String platform,
        @Schema(description = "Адрес страницы согласия") String authorizationUrl
) {
}
