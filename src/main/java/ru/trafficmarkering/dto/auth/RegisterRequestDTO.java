package ru.trafficmarkering.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
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

    @NotBlank(message = "Не указан e-mail")
    @Email(message = "Некорректный e-mail")
    @Size(max = 255)
    @Schema(description = "Почта, она же логин", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "creator@traffic.ru")
    private String email;

    @NotBlank(message = "Имя обязательно")
    @Size(min = 1, max = 255)
    @Schema(description = "Имя (как обращаться к человеку)", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "Аня")
    private String name;

    @NotNull(message = "Роль обязательна: CUSTOMER или CREATOR")
    @Schema(description = "Роль: CUSTOMER (заказчик) или CREATOR (криатор)",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "CREATOR")
    private Role role;
}
