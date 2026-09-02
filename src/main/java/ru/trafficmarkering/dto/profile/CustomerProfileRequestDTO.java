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
@Schema(description = "Сохранение профиля заказчика")
public class CustomerProfileRequestDTO {

    @Size(max = 255)
    @Schema(description = "Название компании — оно видно на карточке объявления", example = "Демо Бренд")
    private String company;

    @Schema(description = "О компании: чем занимаетесь, что рекламируете")
    private String about;

    @Size(max = 255)
    @Schema(description = "Telegram для связи с криаторами", example = "@demo_brand")
    private String telegram;

    @Size(max = 255)
    @Schema(description = "Сайт компании", example = "https://example.ru")
    private String website;
}
