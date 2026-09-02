package ru.trafficmarkering.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос на вход")
public class LoginRequestDTO {

    @NotBlank(message = "Логин обязателен")
    @Schema(description = "Логин (e-mail)", required = true, example = "demo-creator@traffic.ru")
    private String username;

    @NotBlank(message = "Пароль обязателен")
    @Schema(description = "Пароль", required = true)
    private String password;
}
