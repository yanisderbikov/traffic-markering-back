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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.application.ApplicationStatusUpdateRequestDTO;
import ru.trafficmarkering.service.application.ApplicationService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/moderation")
@RequiredArgsConstructor
@Tag(name = "AdminModeration", description = "Модерация роликов: до одобрения начисления по отклику не идут")
public class AdminModerationController {

    private final ApplicationService applicationService;

    @Operation(summary = "Очередь модерации",
            description = "Отклики с приложенным роликом, по которым ещё нет решения. Старые сверху",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/applications")
    public ResponseEntity<List<ApplicationDTO>> moderationQueue() {
        return ResponseEntity.ok(applicationService.getModerationQueue());
    }

    @Operation(summary = "Решение модерации",
            description = "APPROVED — ролик принят, по нему начинают капать деньги; REJECTED — отказ с обязательной "
                    + "причиной, её видит криатор. Решить можно только отклик в статусе PENDING, иначе 409",
            security = @SecurityRequirement(name = "Bearer"))
    @PatchMapping("/applications/{id}")
    public ResponseEntity<ApplicationDTO> moderate(@PathVariable("id") UUID id,
                                                   @Valid @RequestBody ApplicationStatusUpdateRequestDTO request) {
        return ResponseEntity.ok(applicationService.moderate(id, request));
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
