package ru.trafficmarkering.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.trafficmarkering.model.Role;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос на регистрацию")
public class RegisterRequestDTO {

    @NotBlank(message = "Логин обязателен")
    @Size(min = 1, max = 255)
    @Schema(description = "Логин (e-mail)", required = true, example = "creator@traffic.ru")
    private String username;

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 6, message = "Пароль не короче 6 символов")
    @Schema(description = "Пароль, минимум 6 символов", required = true)
    private String password;

    @NotBlank(message = "Имя обязательно")
    @Size(min = 1, max = 255)
    @Schema(description = "Имя (как обращаться к человеку)", required = true, example = "Аня")
    private String name;

    /** Регистрироваться можно только заказчиком или криатором: ADMIN и SERVICE снаружи не выдаются. */
    @NotNull(message = "Роль обязательна: CUSTOMER или CREATOR")
    @Schema(description = "Роль: CUSTOMER (заказчик) или CREATOR (криатор)", required = true, example = "CREATOR")
    private Role role;
}
