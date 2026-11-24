package ru.trafficmarkering.controller;

import ru.trafficmarkering.dto.notify.BroadcastDTO;
import ru.trafficmarkering.dto.notify.BroadcastRequestDTO;
import ru.trafficmarkering.dto.notify.ReachDTO;
import ru.trafficmarkering.service.notify.BroadcastService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.admin.AdminUserDTO;
import ru.trafficmarkering.dto.admin.AssignRoleRequestDTO;
import ru.trafficmarkering.dto.admin.PlatformDashboardDTO;
import ru.trafficmarkering.dto.admin.PlatformSeriesDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.repository.SeriesStep;
import ru.trafficmarkering.service.admin.PlatformDashboardService;
import ru.trafficmarkering.service.admin.SuperAdminService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Hidden
@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
public class SuperAdminController {

    private final SuperAdminService superAdminService;
    private final PlatformDashboardService platformDashboardService;
    private final BroadcastService broadcastService;

    @GetMapping("/dashboard")
    public ResponseEntity<PlatformDashboardDTO> dashboard(@RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(platformDashboardService.dashboard(days));
    }

    @GetMapping("/dashboard/series")
    public ResponseEntity<PlatformSeriesDTO> dashboardSeries(@RequestParam(required = false) Integer days,
                                                             @RequestParam SeriesStep step,
                                                             @RequestParam(required = false) String tz) {
        return ResponseEntity.ok(platformDashboardService.series(days, step, tz));
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserDTO>> adminUsers() {
        return ResponseEntity.ok(superAdminService.users());
    }

    @PostMapping("/users")
    public ResponseEntity<AdminUserDTO> assignRole(@Valid @RequestBody AssignRoleRequestDTO request) {
        return ResponseEntity.ok(superAdminService.assignRole(request));
    }

    @GetMapping("/notifications/reach")
    public ResponseEntity<List<ReachDTO>> notificationReach() {
        return ResponseEntity.ok(broadcastService.reach());
    }

    @GetMapping("/broadcasts")
    public ResponseEntity<List<BroadcastDTO>> broadcasts() {
        return ResponseEntity.ok(broadcastService.list());
    }

    /** Рассылка ставится в очередь и уходит в фоне; test=true — только себе, без истории (ответ пустой) */
    @PostMapping("/broadcasts")
    public ResponseEntity<BroadcastDTO> broadcast(@Valid @RequestBody BroadcastRequestDTO request) {
        BroadcastDTO created = broadcastService.create(request);
        return created == null ? ResponseEntity.accepted().build() : ResponseEntity.ok(created);
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Некорректный параметр " + e.getName() + ": " + e.getValue()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        String allowed = Role.assignable().stream().map(Role::name).collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Некорректное тело запроса: роль должна быть одной из " + allowed));
    }
}
