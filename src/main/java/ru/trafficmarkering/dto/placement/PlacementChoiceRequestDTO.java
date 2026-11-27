package ru.trafficmarkering.dto.placement;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Вариант участия «ссылка и промокод»: какие надбавки креатор берёт на себя")
public class PlacementChoiceRequestDTO {

    @Schema(description = "Поставлю рекламную ссылку в профиль")
    private boolean withLink;

    @Schema(description = "Закреплю комментарий с промокодом под роликом")
    private boolean withPromo;
}
