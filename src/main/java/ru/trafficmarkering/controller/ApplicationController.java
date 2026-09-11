package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.application.ApplicationCreateRequestDTO;
import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.application.ApplicationStatusUpdateRequestDTO;
import ru.trafficmarkering.dto.application.ViewSnapshotDTO;
import ru.trafficmarkering.service.application.ApplicationService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@Tag(name = "Application", description = "Отклики криаторов на объявления")
public class ApplicationController {

    private final ApplicationService applicationService;

    @Operation(summary = "Взять объявление в работу",
            description = "Криатор прикладывает ссылку на ролик; площадка определяется по ссылке, "
                    + "а аккаунт этой площадки должен быть привязан в профиле. Откликнуться можно только "
                    + "на активное объявление и не на своё, роликов на одно объявление можно подать сколько угодно; тот же ролик повторно — 409",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping
    public ResponseEntity<ApplicationDTO> apply(@Valid @RequestBody ApplicationCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.apply(request));
    }

    @Operation(summary = "Мои отклики",
            description = "Отклики текущего криатора со ставкой объявления, просмотрами и начислением. Новые сверху",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/my")
    public ResponseEntity<List<ApplicationDTO>> myApplications() {
        return ResponseEntity.ok(applicationService.getMyApplications());
    }

    @Operation(summary = "Решение по отклику",
            description = "Заказчик объявления одобряет (APPROVED), отклоняет (REJECTED) или завершает (COMPLETED) отклик. "
                    + "После смены статуса начисления по объявлению пересчитываются целиком",
            security = @SecurityRequirement(name = "Bearer"))
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationDTO> updateApplicationStatus(@PathVariable("id") UUID id,
                                                                  @Valid @RequestBody ApplicationStatusUpdateRequestDTO request) {
        return ResponseEntity.ok(applicationService.updateStatus(id, request));
    }

    @Operation(summary = "Отозвать отклик",
            description = "Криатор убирает свой отклик, пока заказчик его не рассмотрел: после решения — 409",
            security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(@PathVariable("id") UUID id) {
        applicationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "История просмотров ролика",
            description = "Замеры просмотров с таймстемпами, свежие сверху — по ним видно динамику ролика. "
                    + "Доступна криатору отклика, заказчику объявления и админу",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/{id}/views/history")
    public ResponseEntity<List<ViewSnapshotDTO>> viewHistory(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(applicationService.viewHistory(id));
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
