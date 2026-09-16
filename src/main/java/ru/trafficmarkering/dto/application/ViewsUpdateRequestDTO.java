package ru.trafficmarkering.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Просмотры ролика от внешнего анализатора")
public class ViewsUpdateRequestDTO {

    @NotNull(message = "Количество просмотров обязательно")
    @PositiveOrZero(message = "Просмотры не могут быть отрицательными")
    @Schema(description = "Накопленное число просмотров ролика", required = true, example = "12400")
    private Long views;

    /**
     * Опционально: разбивка просмотров по странам (ISO 3166-1 alpha-2), если анализатор
     * умеет отдавать гео. null — гео не пришло, регион по этому обновлению не подтверждаем
     * (см. Region, CampaignAccrualService.eligibleViews).
     */
    @Schema(description = "Разбивка просмотров по странам, ISO 3166-1 alpha-2; "
            + "null — анализатор не отдаёт гео", example = "{\"RU\": 10000, \"BY\": 2400}")
    private Map<String, Long> viewsByCountry;
}
