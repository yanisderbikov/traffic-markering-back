package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.social.SocialAccountDTO;
import ru.trafficmarkering.dto.social.SocialAuthorizeResponseDTO;
import ru.trafficmarkering.service.social.SocialAccountService;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "SocialAccounts", description = "Привязка аккаунтов соцсетей к профилю криатора")
public class SocialAccountController {

    private final SocialAccountService socialAccountService;

    @Operation(summary = "Мои привязанные аккаунты",
            description = "Все площадки одним списком; ограничения на количество нет",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/accounts")
    public ResponseEntity<List<SocialAccountDTO>> listAccounts() {
        return ResponseEntity.ok(socialAccountService.listMine());
    }

    @Operation(summary = "Начать привязку аккаунта",
            description = "Возвращает адрес страницы согласия площадки: фронт открывает его в браузере. "
                    + "Платформа в пути: instagram, tiktok, youtube",
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
}
