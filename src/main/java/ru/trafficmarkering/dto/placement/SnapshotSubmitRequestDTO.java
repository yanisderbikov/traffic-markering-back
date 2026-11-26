package ru.trafficmarkering.dto.placement;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Подтверждение расчёта и подача работы «ссылка и промокод»")
public class SnapshotSubmitRequestDTO {

    @NotNull(message = "Нет расчёта: сначала рассчитайте работу")
    @Schema(description = "snapshotId из расчёта, который криатор видел и подтверждает", required = true)
    private Long snapshotId;

    @Schema(description = "Вариант из расчёта: ссылка в профиле; должен совпасть с текущим выбором")
    private boolean withLink;

    @Schema(description = "Вариант из расчёта: промокод в закрепе; должен совпасть с текущим выбором")
    private boolean withPromo;

    @Size(max = 512)
    @Schema(description = "Скриншот профиля со ссылкой из /api/files/placement-proof/presign; "
            + "обязателен, если автопроверка ссылку не нашла")
    private String linkProofKey;

    @Size(max = 512)
    @Schema(description = "Скриншот или запись экрана с закреплённым комментарием; обязателен для надбавки за промокод")
    private String promoProofKey;

    @Size(max = 1024)
    @Schema(description = "Ссылка на сам закреплённый комментарий, если площадка её даёт")
    private String promoCommentUrl;

    @Schema(description = "Комментарий заказчику")
    private String comment;
}
