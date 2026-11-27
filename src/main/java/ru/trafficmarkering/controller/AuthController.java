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
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.auth.telegram.TelegramAuthService;
import ru.trafficmarkering.service.auth.telegram.TelegramInitData;
import ru.trafficmarkering.dto.auth.EmailCodeDTO;
import ru.trafficmarkering.dto.auth.TelegramAuthPollDTO;
import ru.trafficmarkering.dto.auth.TelegramAuthSecretDTO;
import ru.trafficmarkering.dto.auth.TelegramAuthStartDTO;
import ru.trafficmarkering.dto.auth.TelegramAuthStartRequestDTO;
import ru.trafficmarkering.dto.auth.TelegramWebAppDTO;
import ru.trafficmarkering.dto.auth.TelegramWebAppRequestDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Вход по коду с почты (register или request-code → письмо → verify → JWT) "
        + "или через Telegram-бота (telegram/start → бот → telegram/poll → JWT)")
public class AuthController {

    private static final Map<String, String> CODE_SENT = Map.of("message", "Код отправлен на почту");

    private final AuthService authService;
    private final TelegramAuthService telegramAuthService;
    private final TelegramInitData telegramInitData;
    private final CurrentUserService currentUserService;

    @Operation(summary = "Регистрация",
            description = "Роль — CUSTOMER (заказчик) или CREATOR (креатор). Заводится учётка с пустым профилем "
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
            description = "Кто пришёл с токеном: id, логин, имя, роль и вкладки кабинета, доступные этой роли",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/me")
    public ResponseEntity<CurrentUserDTO> me() {
        return ResponseEntity.ok(authService.me());
    }

    @Operation(summary = "Вход через Telegram: начать",
            description = "Отдаёт ссылку в бота и секрет. Браузер открывает ссылку и опрашивает /telegram/poll. "
                    + "Роль передают только со страницы регистрации — для новой учётки")
    @PostMapping("/telegram/start")
    public ResponseEntity<TelegramAuthStartDTO> telegramStart(
            @Valid @RequestBody(required = false) TelegramAuthStartRequestDTO request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        TelegramAuthStartRequestDTO body = request == null ? new TelegramAuthStartRequestDTO(null, null) : request;
        return ResponseEntity.ok(telegramAuthService.start(body.role(), body.referralCode(), describeClient(userAgent)));
    }

    @Operation(summary = "Вход из мини-приложения Telegram",
            description = "Принимает Telegram.WebApp.initData, проверяет подпись бота. Привязанный Telegram — сразу JWT; "
                    + "непривязанный — NEED_ACCOUNT, затем повторный запрос с action cr/cu/have")
    @PostMapping("/telegram/webapp")
    public ResponseEntity<TelegramWebAppDTO> telegramWebApp(@Valid @RequestBody TelegramWebAppRequestDTO request) {
        return ResponseEntity.ok(telegramAuthService.webApp(telegramInitData.verify(request.initData()),
                request.action(), request.referralCode()));
    }

    @Operation(summary = "Вход через Telegram: результат",
            description = "PENDING — ждём бота, DONE — в ответе токен (выдаётся один раз), "
                    + "NEED_EMAIL — войдите по почте и вызовите /telegram/attach, CANCELLED, EXPIRED")
    @PostMapping("/telegram/poll")
    public ResponseEntity<TelegramAuthPollDTO> telegramPoll(@Valid @RequestBody TelegramAuthSecretDTO request) {
        return ResponseEntity.ok(telegramAuthService.poll(request.secret()));
    }

    @Operation(summary = "Привязать Telegram после входа по почте",
            description = "Для ответа NEED_EMAIL: человек подтвердил Telegram в боте, но учётки у него не было "
                    + "под этим Telegram — после входа по почте он привязывается к ней",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/telegram/attach")
    public ResponseEntity<Map<String, String>> telegramAttach(@Valid @RequestBody TelegramAuthSecretDTO request) {
        String telegram = telegramAuthService.attach(request.secret(), currentUserService.require());
        return ResponseEntity.ok(Map.of("message", "Telegram привязан", "telegram", telegram == null ? "" : telegram));
    }

    @Operation(summary = "Добавить почту к учётке из Telegram",
            description = "Без code — отправляет письмо с кодом; с code — подтверждает, почта становится логином "
                    + "и в ответе новый токен. 409 — почта занята или у учётки уже есть почта",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/email")
    public ResponseEntity<?> addEmail(@Valid @RequestBody EmailCodeDTO request) {
        if (request.code() == null || request.code().isBlank()) {
            authService.requestEmailLink(request.email());
            return ResponseEntity.ok(CODE_SENT);
        }
        return ResponseEntity.ok(authService.confirmEmailLink(request.email(), request.code()));
    }

    /** Коротко, чем входят: показываем в боте, чтобы человек узнал свою попытку */
    static String describeClient(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String ua = userAgent;
        String browser = ua.contains("YaBrowser") ? "Яндекс Браузер" : ua.contains("Edg/") ? "Edge"
                : ua.contains("OPR/") ? "Opera" : ua.contains("Firefox/") ? "Firefox"
                : ua.contains("Chrome/") ? "Chrome" : ua.contains("Safari/") ? "Safari" : "браузер";
        String os = ua.contains("iPhone") || ua.contains("iPad") ? "iOS" : ua.contains("Android") ? "Android"
                : ua.contains("Mac OS X") ? "macOS" : ua.contains("Windows") ? "Windows"
                : ua.contains("Linux") ? "Linux" : null;
        return os == null ? browser : browser + ", " + os;
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
