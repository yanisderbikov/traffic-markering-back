package ru.trafficmarkering.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Запрос кода входа на почту")
public class RequestCodeDTO {

    @NotBlank(message = "Не указан e-mail")
    @Email(message = "Некорректный e-mail")
    @Schema(description = "Почта, на которую придёт код", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "demo-creator@traffic.ru")
    private String email;
}
