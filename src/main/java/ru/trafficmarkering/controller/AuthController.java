package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import ru.trafficmarkering.dto.CurrentUserDTO;
import ru.trafficmarkering.dto.LoginRequestDTO;
import ru.trafficmarkering.dto.LoginResponseDTO;
import ru.trafficmarkering.dto.RegisterRequestDTO;
import ru.trafficmarkering.service.auth.AuthService;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Регистрация, вход и текущий пользователь")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Регистрация",
            description = "Роль — CUSTOMER (заказчик) или CREATOR (криатор). "
                    + "Сразу заводится пустой профиль нужного типа, в ответе — токен: логиниться повторно не нужно. "
                    + "409, если логин уже занят")
    @PostMapping("/register")
    public ResponseEntity<LoginResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(summary = "Вход, получение токена",
            description = "401 и одинаковый текст на неверный логин и на неверный пароль")
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Текущий пользователь",
            description = "Кто пришёл с токеном: id, логин, имя и роль",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/me")
    public ResponseEntity<CurrentUserDTO> me() {
        return ResponseEntity.ok(authService.me());
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

    /** Неизвестное значение роли Jackson роняет ещё до валидации — объясняем это по-человечески. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Некорректное тело запроса: роль должна быть CUSTOMER или CREATOR"));
    }
}
