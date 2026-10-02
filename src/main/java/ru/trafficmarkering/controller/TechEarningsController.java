package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.service.earnings.EarningsService;

import java.util.Map;

@RestController
@RequestMapping("/api/tech/earnings")
@RequiredArgsConstructor
@Tag(name = "TechEarnings", description = "Ручной запуск ночного начисления в кошельки криаторов")
public class TechEarningsController {

    private final EarningsService earningsService;

    @Operation(summary = "Начислить накопленное сейчас",
            description = "То же, что делает ночной шедулер: по каждому отклику разница между начисленным "
                    + "и уже зачисленным уходит в кошелёк криатора. Ответ — сколько откликов зачислено",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/credit")
    public ResponseEntity<Map<String, Integer>> creditEarnings() {
        return ResponseEntity.ok(Map.of("credited", earningsService.creditAccrued()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Ошибка запроса" : e.getReason()));
    }
}
