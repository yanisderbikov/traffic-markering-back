package ru.trafficmarkering.dto.partner;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Кто пригласил: показывается на лендинге рекламодателя по коду из ссылки")
public record PartnerInviteDTO(
        @Schema(description = "Код приглашения", example = "K7Q2M9XA") String code,
        @Schema(description = "Имя партнёра") String name,
        @Schema(description = "Компания партнёра; null — не указана в профиле") String company
) {
}
