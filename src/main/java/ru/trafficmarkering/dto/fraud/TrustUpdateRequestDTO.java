package ru.trafficmarkering.dto.fraud;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.trafficmarkering.model.fraud.TrustLevel;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ручная смена репутации криатора")
public class TrustUpdateRequestDTO {

    @Schema(description = "NEW, TRUSTED, RESTRICTED, BLOCKED; null — снять ручную отметку и вернуть автоматике")
    private TrustLevel trustLevel;

    @Size(max = 2000, message = "Комментарий не длиннее 2000 символов")
    private String note;
}
