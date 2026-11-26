package ru.trafficmarkering.dto.moderation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Список менеджеров модерации целиком: кого нет в списке — заявки ему больше не приходят")
public record ModeratorsUpdateRequestDTO(
        @NotNull(message = "Передайте список ников")
        @Size(max = 500, message = "Не больше 500 строк")
        @Schema(description = "Ники Telegram, можно с @; в одной строке — несколько через пробел или запятую",
                example = "[\"@ivan_moder\", \"@maria_check\"]")
        List<String> usernames
) {
}
