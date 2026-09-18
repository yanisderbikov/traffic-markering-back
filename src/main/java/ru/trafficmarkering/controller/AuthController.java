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
import ru.trafficmarkering.dto.auth.AuthResponseDTO;
import ru.trafficmarkering.dto.auth.RegisterRequestDTO;
import ru.trafficmarkering.dto.auth.RequestCodeDTO;
import ru.trafficmarkering.dto.auth.VerifyCodeDTO;
import ru.trafficmarkering.service.auth.AuthService;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Вход по коду с почты: register или request-code → письмо → verify → JWT")
public class AuthController {

    private static final Map<String, String> CODE_SENT = Map.of("message", "Код отправлен на почту");

    private final AuthService authService;

    @Operation(summary = "Регистрация",
            description = "Роль — CUSTOMER (заказчик) или CREATOR (криатор). Заводится учётка с пустым профилем "
                    + "нужного типа и на почту уходит код входа; токен выдаёт verify. "
                    + "409, если почта уже занята подтверждённой учёткой")
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequestDTO request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CODE_SENT);
    }

    @Operation(summary = "Отправить код входа на почту",
            description = "404, если учётки с такой почтой нет; 429, если код уже уходил меньше 30 секунд назад")
    @PostMapping("/request-code")
    public ResponseEntity<Map<String, String>> requestCode(@Valid @RequestBody RequestCodeDTO request) {
        authService.requestCode(request.getEmail());
        return ResponseEntity.ok(CODE_SENT);
    }

    @Operation(summary = "Обменять код на JWT",
            description = "Код живёт 10 минут, не больше 5 попыток ввода. "
                    + "Ответ: token (Bearer), role, email, name")
    @PostMapping("/verify")
    public ResponseEntity<AuthResponseDTO> verify(@Valid @RequestBody VerifyCodeDTO request) {
        return ResponseEntity.ok(authService.verify(request.getEmail(), request.getCode()));
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Некорректное тело запроса: роль должна быть CUSTOMER или CREATOR"));
    }
}
