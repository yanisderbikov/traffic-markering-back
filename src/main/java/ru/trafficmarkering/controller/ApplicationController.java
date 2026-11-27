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
import ru.trafficmarkering.dto.application.ApplicationCreateRequestDTO;
import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.application.ApplicationStatusUpdateRequestDTO;
import ru.trafficmarkering.dto.application.ApplicationVideoRequestDTO;
import ru.trafficmarkering.dto.application.ViewSnapshotDTO;
import ru.trafficmarkering.dto.placement.PlacementChoiceRequestDTO;
import ru.trafficmarkering.dto.placement.PlacementDecisionRequestDTO;
import ru.trafficmarkering.dto.placement.PlacementProofRequestDTO;
import ru.trafficmarkering.dto.placement.SnapshotQuoteDTO;
import ru.trafficmarkering.dto.placement.SnapshotQuoteRequestDTO;
import ru.trafficmarkering.dto.placement.SnapshotSubmitRequestDTO;
import ru.trafficmarkering.model.application.PlacementKind;
import ru.trafficmarkering.service.application.ApplicationService;
import ru.trafficmarkering.service.application.PlacementService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@Tag(name = "Application", description = "Отклики креаторов на объявления")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final PlacementService placementService;

    @Operation(summary = "Взять объявление в работу",
            description = "Без ссылки на ролик отклик встаёт в IN_PROGRESS: креатор снимает ролик и прикладывает его позже, "
                    + "повторный вызов возвращает тот же отклик. Со ссылкой площадка определяется по ней, "
                    + "должна быть среди площадок объявления, а аккаунт этой площадки должен быть привязан в профиле. "
                    + "Откликнуться можно только "
                    + "на активное объявление и не на своё, роликов на одно объявление можно подать сколько угодно; тот же ролик повторно — 409",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping
    public ResponseEntity<ApplicationDTO> apply(@Valid @RequestBody ApplicationCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.apply(request));
    }

    @Operation(summary = "Приложить ролик к работе",
            description = "Ролик к отклику в статусе IN_PROGRESS: те же проверки площадки, аккаунта и дублей, что и при отклике "
                    + "со ссылкой. После этого отклик уходит заказчику на рассмотрение (PENDING)",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/{id}/video")
    public ResponseEntity<ApplicationDTO> attachVideo(@PathVariable("id") UUID id,
                                                      @Valid @RequestBody ApplicationVideoRequestDTO request) {
        return ResponseEntity.ok(applicationService.attachVideo(id, request));
    }

    @Operation(summary = "Мои отклики",
            description = "Отклики текущего креатора со ставкой объявления, просмотрами и начислением. Новые сверху",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/my")
    public ResponseEntity<List<ApplicationDTO>> myApplications() {
        return ResponseEntity.ok(applicationService.getMyApplications());
    }

    @Operation(summary = "Решение по отклику",
            description = "Заказчик объявления одобряет (APPROVED), отклоняет (REJECTED, причина обязательна) или завершает (COMPLETED) отклик. "
                    + "После смены статуса начисления по объявлению пересчитываются целиком",
            security = @SecurityRequirement(name = "Bearer"))
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationDTO> updateApplicationStatus(@PathVariable("id") UUID id,
                                                                  @Valid @RequestBody ApplicationStatusUpdateRequestDTO request) {
        return ResponseEntity.ok(applicationService.updateStatus(id, request));
    }

    @Operation(summary = "Отозвать отклик",
            description = "Креатор убирает свой отклик или отказывается от взятой работы, пока заказчик не принял решение: после решения — 409",
            security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(@PathVariable("id") UUID id) {
        applicationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "История просмотров ролика",
            description = "Замеры просмотров с таймстемпами, свежие сверху — по ним видно динамику ролика. "
                    + "Доступна креатору отклика, заказчику объявления и админу",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/{id}/views/history")
    public ResponseEntity<List<ViewSnapshotDTO>> viewHistory(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(applicationService.viewHistory(id));
    }

    // ---- «Ссылка и промокод»: оплата по снимку просмотров при подаче ----

    @Operation(summary = "Вариант участия «ссылка и промокод»",
            description = "Какие надбавки креатор берёт на себя: ссылку в профиле, промокод в закрепе, оба или ни одной. "
                    + "Менять можно, пока работа не подана",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/{id}/placements")
    public ResponseEntity<ApplicationDTO> choosePlacements(@PathVariable("id") UUID id,
                                                           @Valid @RequestBody PlacementChoiceRequestDTO request) {
        return ResponseEntity.ok(placementService.choose(id, request));
    }

    @Operation(summary = "Расчёт работы по снимку просмотров",
            description = "Проверяет ролик (площадка, аккаунт, дубли, выложен после взятия оффера), снимает свежие "
                    + "просмотры и проверяет ссылку в профиле. Возвращает расчёт: оплачиваемые просмотры, базу, надбавки, "
                    + "комиссию. Расчёт живёт 15 минут и подтверждается подачей",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/{id}/quote")
    public ResponseEntity<SnapshotQuoteDTO> quoteWork(@PathVariable("id") UUID id,
                                                      @Valid @RequestBody SnapshotQuoteRequestDTO request) {
        return ResponseEntity.ok(placementService.quote(id, request));
    }

    @Operation(summary = "Подать работу по расчёту",
            description = "Фиксирует просмотры и сумму из расчёта snapshotId; рост просмотров после подачи работу не меняет. "
                    + "Для надбавки за промокод нужен скриншот или запись экрана с закрепом; для ссылки — скриншот, "
                    + "если автопроверка её не нашла. Устаревший расчёт — 409",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApplicationDTO> submitWork(@PathVariable("id") UUID id,
                                                     @Valid @RequestBody SnapshotSubmitRequestDTO request) {
        return ResponseEntity.ok(placementService.submit(id, request));
    }

    @Operation(summary = "Исправить надбавку",
            description = "Новое доказательство по надбавке link или promo после отказа или вместо прежнего: надбавка снова "
                    + "ждёт проверки, снимок просмотров и база не меняются",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/{id}/placements/{kind}/proof")
    public ResponseEntity<ApplicationDTO> resubmitPlacementProof(@PathVariable("id") UUID id,
                                                                 @PathVariable("kind") String kind,
                                                                 @Valid @RequestBody PlacementProofRequestDTO request) {
        return ResponseEntity.ok(placementService.resubmitProof(id, requireKind(kind), request));
    }

    @Operation(summary = "Отказаться от надбавки",
            description = "Креатор продолжает без надбавки link или promo по меньшей ставке; база и другая надбавка остаются",
            security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/{id}/placements/{kind}")
    public ResponseEntity<ApplicationDTO> declinePlacement(@PathVariable("id") UUID id,
                                                           @PathVariable("kind") String kind) {
        return ResponseEntity.ok(placementService.decline(id, requireKind(kind)));
    }

    @Operation(summary = "Решение по надбавке",
            description = "Заказчик объявления или модератор подтверждает (VERIFIED) или отклоняет (REJECTED, с причиной) "
                    + "надбавку link или promo. Отказ по надбавке не отклоняет ролик. Засчитанную надбавку не изменить",
            security = @SecurityRequirement(name = "Bearer"))
    @PatchMapping("/{id}/placements/{kind}/decision")
    public ResponseEntity<ApplicationDTO> decidePlacement(@PathVariable("id") UUID id,
                                                          @PathVariable("kind") String kind,
                                                          @Valid @RequestBody PlacementDecisionRequestDTO request) {
        return ResponseEntity.ok(placementService.decide(id, requireKind(kind), request));
    }

    @Operation(summary = "Проверить ссылку в профиле ещё раз",
            description = "Автопроверка ссылки по публичному профилю; найденная ссылка подтверждает ожидающую надбавку. "
                    + "Не чаще раза в минуту",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/{id}/placements/link/check")
    public ResponseEntity<ApplicationDTO> recheckPlacementLink(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(placementService.recheckLink(id));
    }

    private static PlacementKind requireKind(String kind) {
        PlacementKind parsed = PlacementKind.fromPath(kind);
        if (parsed == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Нет такой надбавки: " + kind);
        }
        return parsed;
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
