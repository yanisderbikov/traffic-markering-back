package ru.trafficmarkering.controller;

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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.admin.AdminUserDTO;
import ru.trafficmarkering.dto.admin.AssignRoleRequestDTO;
import ru.trafficmarkering.model.Role;
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

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserDTO>> adminUsers() {
        return ResponseEntity.ok(superAdminService.users());
    }

    @PostMapping("/users")
    public ResponseEntity<AdminUserDTO> assignRole(@Valid @RequestBody AssignRoleRequestDTO request) {
        return ResponseEntity.ok(superAdminService.assignRole(request));
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        String allowed = Role.assignable().stream().map(Role::name).collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Некорректное тело запроса: роль должна быть одной из " + allowed));
    }
}
