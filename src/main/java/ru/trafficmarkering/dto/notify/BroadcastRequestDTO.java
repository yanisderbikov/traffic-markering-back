package ru.trafficmarkering.dto.notify;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.trafficmarkering.model.notify.BroadcastAudience;
import ru.trafficmarkering.model.notify.BroadcastChannel;

public record BroadcastRequestDTO(
        @NotNull(message = "Выберите, кому отправить") BroadcastAudience audience,
        @NotNull(message = "Выберите канал") BroadcastChannel channel,
        @NotBlank(message = "Нужен заголовок") @Size(max = 200, message = "Заголовок — до 200 символов") String title,
        @NotBlank(message = "Нужен текст") @Size(max = 3500, message = "Текст — до 3500 символов") String text,
        @Size(max = 64, message = "Текст кнопки — до 64 символов") String buttonText,
        @Size(max = 512) @Pattern(regexp = "^$|^/.*|^https://.*", message = "Ссылка кнопки: путь /app/... или https://")
        String buttonUrl,
        /** true — отправить только себе, чтобы посмотреть, как выглядит */
        boolean test
) {
}
