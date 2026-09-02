package ru.trafficmarkering.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
