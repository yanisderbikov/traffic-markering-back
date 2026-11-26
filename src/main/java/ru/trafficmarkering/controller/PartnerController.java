package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.partner.PartnerDTO;
import ru.trafficmarkering.dto.partner.PartnerInviteDTO;
import ru.trafficmarkering.service.partner.PartnerService;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Partner", description = "Партнёрская программа: рекламодатель приглашает других и получает долю комиссии платформы с них")
public class PartnerController {

    private final PartnerService partnerService;

    @Operation(summary = "Моя партнёрская программа",
            description = "Условия программы, а если она подключена — код приглашения, приглашённые и начисления",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/api/partner")
    public ResponseEntity<PartnerDTO> myPartner() {
        return ResponseEntity.ok(partnerService.my());
    }

    @Operation(summary = "Стать партнёром",
            description = "Заводит партнёра с кодом приглашения и открывает вкладку «Рефералка»; повторный вызов ничего не меняет",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/api/partner")
    public ResponseEntity<PartnerDTO> activatePartner() {
        return ResponseEntity.ok(partnerService.activate());
    }

    @Operation(summary = "Кто пригласил",
            description = "Имя и компания партнёра по коду из ссылки-приглашения; 404 — код не найден")
    @GetMapping("/api/public/partners/{code}")
    public ResponseEntity<PartnerInviteDTO> partnerInvite(@PathVariable("code") String code) {
        return ResponseEntity.ok(partnerService.invite(code));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Ошибка запроса" : e.getReason()));
    }
}
