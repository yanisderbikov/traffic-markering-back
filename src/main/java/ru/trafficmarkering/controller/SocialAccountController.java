package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.social.SocialAccountDTO;
import ru.trafficmarkering.dto.social.SocialAuthorizeResponseDTO;
import ru.trafficmarkering.dto.social.SocialModeDTO;
import ru.trafficmarkering.dto.social.SocialVerificationDTO;
import ru.trafficmarkering.dto.social.SocialVerificationRequestDTO;
import ru.trafficmarkering.service.social.SocialAccountService;
import ru.trafficmarkering.service.social.SocialVerificationService;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "SocialAccounts", description = "Привязка аккаунтов соцсетей к профилю криатора")
public class SocialAccountController {

    private final SocialAccountService socialAccountService;
    private final SocialVerificationService socialVerificationService;

    @Operation(summary = "Способ привязки аккаунтов",
            description = "OAUTH — кнопка ведёт на /{platform}/authorize; SCRAPE — криатор вводит ник, "
                    + "получает код через /{platform}/verification и подтверждает через /{platform}/verification/confirm",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/mode")
    public ResponseEntity<SocialModeDTO> mode() {
        return ResponseEntity.ok(socialAccountService.mode());
    }

    @Operation(summary = "Найти профиль и получить проверочный код",
            description = "Только в режиме SCRAPE. Ничего не сохраняет: код один и тот же при каждом запросе "
                    + "для пары «криатор — аккаунт». Платформа в пути: instagram, tiktok, youtube",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/{platform}/verification")
    public ResponseEntity<SocialVerificationDTO> startVerification(@PathVariable("platform") String platform,
                                                                   @Valid @RequestBody SocialVerificationRequestDTO request) {
        return ResponseEntity.ok(socialVerificationService.start(platform, request.account()));
    }

    @Operation(summary = "Подтвердить аккаунт кодом в описании профиля",
            description = "Только в режиме SCRAPE. Перечитывает профиль и привязывает аккаунт, если код на месте; "
                    + "после привязки код из описания можно убрать",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/{platform}/verification/confirm")
    public ResponseEntity<SocialAccountDTO> confirmVerification(@PathVariable("platform") String platform,
                                                                @Valid @RequestBody SocialVerificationRequestDTO request) {
        return ResponseEntity.ok(socialVerificationService.confirm(platform, request.account()));
    }

    @Operation(summary = "Мои привязанные аккаунты",
            description = "Все площадки одним списком; ограничения на количество нет",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/accounts")
    public ResponseEntity<List<SocialAccountDTO>> listAccounts() {
        return ResponseEntity.ok(socialAccountService.listMine());
    }

    @Operation(summary = "Начать привязку аккаунта",
            description = "Только в режиме OAUTH. Возвращает адрес страницы согласия площадки: фронт открывает его "
                    + "в браузере. Платформа в пути: instagram, tiktok, youtube",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/{platform}/authorize")
    public ResponseEntity<SocialAuthorizeResponseDTO> authorize(@PathVariable("platform") String platform) {
        return ResponseEntity.ok(socialAccountService.authorize(platform));
    }

    @Operation(summary = "Возврат с площадки после согласия",
            description = "Сюда браузер приходит редиректом от площадки. Ручка открыта: пользователя "
                    + "опознаём по подписанному state, а не по заголовку авторизации")
    @GetMapping("/callback/{platform}")
    public ResponseEntity<Void> callback(@PathVariable("platform") String platform,
                                         @RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "state", required = false) String state,
                                         @RequestParam(value = "error", required = false) String error) {
        if (error != null) {
            return redirect(socialAccountService.frontRedirect(platform, "denied", null));
        }
        try {
            SocialAccountDTO account = socialAccountService.connect(platform, code, state);
            return redirect(socialAccountService.frontRedirect(platform, "connected", account.username()));
        } catch (ResponseStatusException e) {
            return redirect(socialAccountService.frontRedirect(platform, "error", e.getReason()));
        } catch (Exception e) {
            log.error("Не удалось привязать аккаунт {}", platform, e);
            return redirect(socialAccountService.frontRedirect(platform, "error",
                    "Не удалось привязать аккаунт, попробуйте ещё раз"));
        }
    }

    @Operation(summary = "Отвязать аккаунт", security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<Void> disconnect(@PathVariable("id") UUID id) {
        socialAccountService.disconnect(id);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
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
