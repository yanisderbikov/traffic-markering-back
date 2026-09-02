package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.profile.CreatorProfileDTO;
import ru.trafficmarkering.dto.profile.CreatorProfileRequestDTO;
import ru.trafficmarkering.dto.profile.CustomerProfileDTO;
import ru.trafficmarkering.dto.profile.CustomerProfileRequestDTO;
import ru.trafficmarkering.service.profile.CreatorProfileService;
import ru.trafficmarkering.service.profile.CustomerProfileService;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Профили криатора и заказчика")
public class ProfileController {

    private final CreatorProfileService creatorProfileService;
    private final CustomerProfileService customerProfileService;

    @Operation(summary = "Мой профиль криатора",
            description = "Профиль текущего криатора; если его почему-то нет — заводится пустой, а не 404",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/creator")
    public ResponseEntity<CreatorProfileDTO> getCreatorProfile() {
        return ResponseEntity.ok(creatorProfileService.get());
    }

    @Operation(summary = "Сохранить профиль криатора",
            description = "Полное обновление полей; пустые строки сохраняются как «не заполнено». "
                    + "Ссылки на площадки видит заказчик при разборе откликов",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/creator")
    public ResponseEntity<CreatorProfileDTO> updateCreatorProfile(@Valid @RequestBody CreatorProfileRequestDTO request) {
        return ResponseEntity.ok(creatorProfileService.update(request));
    }

    @Operation(summary = "Мой профиль заказчика",
            description = "Профиль текущего заказчика; если его почему-то нет — заводится пустой, а не 404",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/customer")
    public ResponseEntity<CustomerProfileDTO> getCustomerProfile() {
        return ResponseEntity.ok(customerProfileService.get());
    }

    @Operation(summary = "Сохранить профиль заказчика",
            description = "Полное обновление полей; название компании попадает на карточку объявления",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/customer")
    public ResponseEntity<CustomerProfileDTO> updateCustomerProfile(@Valid @RequestBody CustomerProfileRequestDTO request) {
        return ResponseEntity.ok(customerProfileService.update(request));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Ошибка запроса" : e.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(Map.of("message", message.isBlank() ? "Некорректный запрос" : message));
    }
}
