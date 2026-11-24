package ru.trafficmarkering.dto.placement;

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
@Schema(description = "Исправление надбавки: новое доказательство после отказа или вместо старого")
public class PlacementProofRequestDTO {

    @Size(max = 512)
    @Schema(description = "Ключ скриншота или записи экрана из /api/files/placement-proof/presign")
    private String proofKey;

    @Size(max = 1024)
    @Schema(description = "Ссылка на закреплённый комментарий или профиль")
    private String proofUrl;
}
