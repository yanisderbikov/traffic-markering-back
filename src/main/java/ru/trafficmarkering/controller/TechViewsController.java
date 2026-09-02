package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.application.ViewsUpdateRequestDTO;
import ru.trafficmarkering.service.application.ApplicationService;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Приём просмотров от внешнего сервиса-анализатора: сама платформа их не считает
 * (см. ManualViewCountProvider). Доступ закрыт ролями SERVICE и ADMIN (см. WebSecurityConfig).
 */
@RestController
@RequestMapping("/api/tech")
@RequiredArgsConstructor
@Tag(name = "TechViews", description = "Просмотры роликов от внешнего анализатора (межсервисный токен)")
public class TechViewsController {

    private final ApplicationService applicationService;

    @Operation(summary = "Проставить просмотры отклику",
            description = "Записывает накопленное число просмотров и время синхронизации, после чего "
                    + "пересчитывает начисления по всему объявлению: остаток бюджета режет выплату",
            security = @SecurityRequirement(name = "Bearer"))
    @PatchMapping("/applications/{id}/views")
    public ResponseEntity<ApplicationDTO> updateViews(@PathVariable("id") UUID id,
                                                      @Valid @RequestBody ViewsUpdateRequestDTO request) {
        return ResponseEntity.ok(applicationService.updateViews(id, request));
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
