package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.moderation.ModerationDecisionDTO;
import ru.trafficmarkering.dto.moderation.ModeratorActiveRequestDTO;
import ru.trafficmarkering.dto.moderation.ModeratorsDTO;
import ru.trafficmarkering.dto.moderation.ModeratorsUpdateRequestDTO;
import ru.trafficmarkering.service.moderation.ModeratorRoster;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/superadmin/moderators")
@RequiredArgsConstructor
@Tag(name = "SuperAdminModerators", description = "Менеджеры модерации в Telegram-боте: список ников и их статистика")
public class SuperAdminModeratorController {

    private final ModeratorRoster roster;

    @Operation(summary = "Менеджеры модерации",
            description = "Список, часы приёма, текущая заявка и счётчики решений; плюс сколько роликов ждёт в очереди",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping
    public ResponseEntity<ModeratorsDTO> list() {
        return ResponseEntity.ok(roster.list());
    }

    @Operation(summary = "Задать список менеджеров",
            description = "Список ников целиком. Новых бот пустит по нику, убранным заявки больше не приходят, "
                    + "а их текущая заявка возвращается в очередь",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping
    public ResponseEntity<ModeratorsDTO> replace(@Valid @RequestBody ModeratorsUpdateRequestDTO request) {
        return ResponseEntity.ok(roster.replace(request.usernames()));
    }

    @Operation(summary = "Сделать менеджера активным или неактивным",
            description = "Неактивному заявки не приходят (текущую он ещё может решить); менеджеру приходит сообщение в бота",
            security = @SecurityRequirement(name = "Bearer"))
    @PatchMapping("/{username}")
    public ResponseEntity<ModeratorsDTO> setActive(@PathVariable("username") String username,
                                                   @RequestBody ModeratorActiveRequestDTO request) {
        return ResponseEntity.ok(roster.setActive(username, request.active()));
    }

    @Operation(summary = "Журнал решений менеджеров",
            description = "Кто какой ролик принял или отклонил, свежие сверху — для выборочной перепроверки",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/decisions")
    public ResponseEntity<List<ModerationDecisionDTO>> decisions(@RequestParam(required = false) String username,
                                                                 @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(roster.decisions(username, limit));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Ошибка запроса" : e.getReason()));
    }
}
