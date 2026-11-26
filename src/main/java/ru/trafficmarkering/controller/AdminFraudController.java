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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.fraud.CreatorTrustDTO;
import ru.trafficmarkering.dto.fraud.FraudApplicationDTO;
import ru.trafficmarkering.dto.fraud.FraudReviewRequestDTO;
import ru.trafficmarkering.dto.fraud.TrustUpdateRequestDTO;
import ru.trafficmarkering.service.fraud.FraudAdminService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Кабинет антифрода для ADMIN и SUPER_ADMIN (см. WebSecurityConfig). */
@RestController
@RequestMapping("/api/admin/fraud")
@RequiredArgsConstructor
@Tag(name = "AdminFraud", description = "Антифрод: подозрительные ролики и репутация криаторов")
public class AdminFraudController {

    private final FraudAdminService fraudAdminService;

    @Operation(summary = "Очередь подозрительных роликов",
            description = "По умолчанию отклики со статусами SUSPICIOUS и FRAUD, самые подозрительные сверху. "
                    + "filter=all — все, filter=<статус> — один статус",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/applications")
    public ResponseEntity<List<FraudApplicationDTO>> applications(
            @RequestParam(value = "filter", required = false) String filter) {
        return ResponseEntity.ok(fraudAdminService.applications(filter));
    }

    @Operation(summary = "Отклик с метриками и решением", security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/applications/{id}")
    public ResponseEntity<FraudApplicationDTO> application(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(fraudAdminService.application(id));
    }

    @Operation(summary = "Решение по отклику",
            description = "VERIFIED размораживает зачисление, FRAUD обнуляет начисление и даёт криатору страйк, "
                    + "AUTO возвращает отклик автоматике. Репутация криатора пересчитывается",
            security = @SecurityRequirement(name = "Bearer"))
    @PatchMapping("/applications/{id}/review")
    public ResponseEntity<FraudApplicationDTO> review(@PathVariable("id") UUID id,
                                                      @Valid @RequestBody FraudReviewRequestDTO request) {
        return ResponseEntity.ok(fraudAdminService.review(id, request));
    }

    @Operation(summary = "Перепроверить отклик", security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/applications/{id}/recheck")
    public ResponseEntity<FraudApplicationDTO> recheck(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(fraudAdminService.recheck(id));
    }

    @Operation(summary = "Перепроверить все ролики в работе", security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/recheck")
    public ResponseEntity<Map<String, Integer>> recheckAll() {
        return ResponseEntity.ok(Map.of("checked", fraudAdminService.recheckAll()));
    }

    @Operation(summary = "Криаторы с репутацией", security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/creators")
    public ResponseEntity<List<CreatorTrustDTO>> creators() {
        return ResponseEntity.ok(fraudAdminService.creators());
    }

    @Operation(summary = "Сменить репутацию криатора",
            description = "Уровень выставляется руками и автоматикой больше не пересчитывается; "
                    + "trustLevel=null снимает ручную отметку",
            security = @SecurityRequirement(name = "Bearer"))
    @PatchMapping("/creators/{userId}/trust")
    public ResponseEntity<CreatorTrustDTO> updateTrust(@PathVariable("userId") Long userId,
                                                       @Valid @RequestBody TrustUpdateRequestDTO request) {
        return ResponseEntity.ok(fraudAdminService.updateTrust(userId, request));
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
