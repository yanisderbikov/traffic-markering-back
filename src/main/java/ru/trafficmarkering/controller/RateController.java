package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.rate.UsdtRateDTO;
import ru.trafficmarkering.service.rate.UsdtRateService;

import java.util.Map;

@RestController
@RequestMapping("/api/public/rates")
@RequiredArgsConstructor
@Tag(name = "Rates", description = "Курсы валют для пересчёта рублёвых сумм в USDT")
public class RateController {

    private final UsdtRateService usdtRateService;

    @Operation(summary = "Курс USDT/RUB",
            description = "Берётся с биржи Rapira и кешируется на минуту; если биржа недоступна — отдаётся последний полученный курс, без него 502")
    @GetMapping("/usdt")
    public ResponseEntity<UsdtRateDTO> usdt() {
        return ResponseEntity.ok(usdtRateService.current());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Ошибка запроса" : e.getReason()));
    }
}
