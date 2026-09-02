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
    @Schema(description = "Telegram для связи", example = "@anya")
    private String telegram;

    @Size(max = 255)
    @Schema(description = "Ссылка или ник в Instagram")
    private String instagram;

    @Size(max = 255)
    @Schema(description = "Ссылка или ник в TikTok")
    private String tiktok;

    @Size(max = 255)
    @Schema(description = "Ссылка на канал YouTube Shorts")
    private String youtubeShorts;
}
