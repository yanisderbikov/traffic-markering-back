package ru.trafficmarkering.dto.admin;

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
public class AssignRoleRequestDTO {

    @NotBlank(message = "Не указан e-mail")
    @Email(message = "Некорректный e-mail")
    @Size(max = 255)
    private String email;

    @Size(max = 255, message = "Имя не длиннее 255 символов")
    private String name;

    @NotNull(message = "Роль обязательна")
    private Role role;
}
