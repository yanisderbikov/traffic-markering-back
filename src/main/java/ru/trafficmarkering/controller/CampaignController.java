package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.campaign.CampaignCreateUpdateRequestDTO;
import ru.trafficmarkering.dto.campaign.CampaignDTO;
import ru.trafficmarkering.dto.campaign.CampaignStatusUpdateRequestDTO;
import ru.trafficmarkering.service.campaign.CampaignService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaign", description = "Объявления заказчика: свои объявления и отклики по ним")
public class CampaignController {

    private final CampaignService campaignService;

    @Operation(summary = "Мои объявления",
            description = "Объявления текущего заказчика, новые сверху; суммы в копейках",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping
    public ResponseEntity<List<CampaignDTO>> myCampaigns() {
        return ResponseEntity.ok(campaignService.getMyCampaigns());
    }

    @Operation(summary = "Создать объявление",
            description = "Ставка и бюджет в копейках; статус можно не передавать — тогда объявление создаётся черновиком",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping
    public ResponseEntity<CampaignDTO> createCampaign(@Valid @RequestBody CampaignCreateUpdateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.create(request));
    }

    @Operation(summary = "Объявление по id",
            description = "Только своё объявление; админ видит любое",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/{id}")
    public ResponseEntity<CampaignDTO> getCampaign(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(campaignService.getById(id));
    }

    @Operation(summary = "Обновить объявление",
            description = "Полное обновление полей; смена ставки или бюджета пересчитывает начисления по откликам",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/{id}")
    public ResponseEntity<CampaignDTO> updateCampaign(@PathVariable("id") UUID id,
                                                      @Valid @RequestBody CampaignCreateUpdateRequestDTO request) {
        return ResponseEntity.ok(campaignService.update(id, request));
    }

    @Operation(summary = "Сменить статус объявления",
            description = "На публичной доске показываются только объявления в статусе ACTIVE",
            security = @SecurityRequirement(name = "Bearer"))
    @PatchMapping("/{id}/status")
    public ResponseEntity<CampaignDTO> updateCampaignStatus(@PathVariable("id") UUID id,
                                                            @Valid @RequestBody CampaignStatusUpdateRequestDTO request) {
        return ResponseEntity.ok(campaignService.updateStatus(id, request));
    }

    @Operation(summary = "Удалить объявление",
            description = "Только пока по объявлению нет откликов, иначе 409: удаление стёрло бы историю начислений криаторам",
            security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable("id") UUID id) {
        campaignService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Отклики по объявлению",
            description = "Криаторы, ссылки на ролики, просмотры и начисленные суммы; старые сверху",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/{id}/applications")
    public ResponseEntity<List<ApplicationDTO>> campaignApplications(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(campaignService.getApplications(id));
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
