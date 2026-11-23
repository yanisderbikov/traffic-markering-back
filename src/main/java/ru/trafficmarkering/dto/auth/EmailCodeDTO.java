package ru.trafficmarkering.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Добавление почты к учётке, заведённой через Telegram. Код пустой — только отправить письмо */
public record EmailCodeDTO(
        @NotBlank(message = "Не указан e-mail") @Email(message = "Некорректный e-mail") @Size(max = 255) String email,
        @Size(max = 16) String code
) {
}
