package ru.trafficmarkering.dto.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Сохранение профиля криатора")
public class CreatorProfileRequestDTO {

    @Size(max = 255)
    @Schema(description = "Как показывать криатора заказчику; пусто — имя из учётки", example = "аня снимает")
    private String displayName;

    @Schema(description = "О себе: формат роликов, аудитория, чем берёшь")
    private String bio;

    @Size(max = 255)
    @Schema(description = "Telegram для связи; null — оставить как есть", example = "@anya")
    private String telegram;
}
