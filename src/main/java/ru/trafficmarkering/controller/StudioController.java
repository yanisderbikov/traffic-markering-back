package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.studio.StudioActiveTemplateRequestDTO;
import ru.trafficmarkering.dto.studio.StudioAssetDTO;
import ru.trafficmarkering.dto.studio.StudioAssetRequestDTO;
import ru.trafficmarkering.dto.studio.StudioJobDTO;
import ru.trafficmarkering.dto.studio.StudioJobRequestDTO;
import ru.trafficmarkering.dto.studio.StudioOverviewDTO;
import ru.trafficmarkering.dto.studio.StudioPresignRequestDTO;
import ru.trafficmarkering.dto.studio.StudioPresignResponseDTO;
import ru.trafficmarkering.dto.studio.StudioRetryRequestDTO;
import ru.trafficmarkering.dto.studio.StudioTelegramLinkDTO;
import ru.trafficmarkering.dto.studio.StudioTemplateDTO;
import ru.trafficmarkering.dto.studio.StudioTemplateRequestDTO;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.studio.StudioProfile;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.studio.StudioException;
import ru.trafficmarkering.service.studio.StudioService;
import ru.trafficmarkering.service.studio.StudioSettings;
import ru.trafficmarkering.service.studio.telegram.StudioNotifier;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Tag(name = "Studio", description = "Видео-студия: монтаж роликов на сайте и в Telegram-боте")
public class StudioController {

    private final StudioService studio;
    private final StudioNotifier notifier;
    private final CurrentUserService currentUser;

    @Operation(summary = "Обзор студии", description = "Лимиты, привязка Telegram, встроенные фоны",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/api/studio/overview")
    public StudioOverviewDTO overview() {
        return studio.overview(currentUser.require());
    }

    @Operation(summary = "Ссылка на загрузку файла",
            description = "Presigned PUT: файл уходит в хранилище напрямую с тем же Content-Type. purpose: SOURCE | BANNER | BACKGROUND",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/api/studio/uploads/presign")
    public StudioPresignResponseDTO presign(@Valid @RequestBody StudioPresignRequestDTO request) {
        return studio.presign(currentUser.require(), request);
    }

    @Operation(summary = "Собрать видео", description = "Ставит задачу в очередь; для профиля 9:16 + 1:1 — две задачи одного пакета",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/api/studio/jobs")
    public ResponseEntity<List<StudioJobDTO>> create(@Valid @RequestBody StudioJobRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studio.createFromWeb(currentUser.require(), request));
    }

    @Operation(summary = "Мои видео", description = "filter: all | active | done | failed",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/api/studio/jobs")
    public List<StudioJobDTO> jobs(@RequestParam(defaultValue = "all") String filter,
                                   @RequestParam(defaultValue = "50") int limit) {
        return studio.list(currentUser.require(), filter, limit);
    }

    @GetMapping("/api/studio/jobs/{id}")
    @Operation(summary = "Задача", security = @SecurityRequirement(name = "Bearer"))
    public StudioJobDTO job(@PathVariable UUID id) {
        return studio.get(currentUser.require(), id);
    }

    @PostMapping("/api/studio/jobs/{id}/cancel")
    @Operation(summary = "Отменить задачу", security = @SecurityRequirement(name = "Bearer"))
    public StudioJobDTO cancel(@PathVariable UUID id) {
        return studio.cancel(currentUser.require(), id);
    }

    @PostMapping("/api/studio/jobs/{id}/retry")
    @Operation(summary = "Повторить задачу", description = "Нужны сохранённые исходники (keepSource). quality: null или \"720\"",
            security = @SecurityRequirement(name = "Bearer"))
    public StudioJobDTO retry(@PathVariable UUID id, @RequestBody(required = false) StudioRetryRequestDTO request) {
        return studio.retry(currentUser.require(), id, request == null ? null : request.quality());
    }

    @PostMapping("/api/studio/jobs/{id}/telegram")
    @Operation(summary = "Прислать готовый MP4 в Telegram", description = "Нужна привязка Telegram; квоту не тратит",
            security = @SecurityRequirement(name = "Bearer"))
    public Map<String, String> sendToTelegram(@PathVariable UUID id) {
        User user = currentUser.require();
        StudioProfile profile = studio.profile(user.getId());
        if (profile.getTelegramId() == null) {
            throw new StudioException("Сначала привяжите Telegram.");
        }
        notifier.sendExisting(studio.own(user, id), profile.getTelegramId());
        return Map.of("message", "Отправлено в Telegram");
    }

    @GetMapping("/api/studio/assets")
    @Operation(summary = "Библиотека", description = "Свои баннеры и фоны и общий каталог",
            security = @SecurityRequirement(name = "Bearer"))
    public List<StudioAssetDTO> assets() {
        return studio.assets(currentUser.require());
    }

    @PostMapping("/api/studio/assets")
    @Operation(summary = "Сохранить файл в библиотеку", description = "shared=true — в общий каталог (только админ)",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<StudioAssetDTO> saveAsset(@Valid @RequestBody StudioAssetRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studio.saveAsset(currentUser.require(), request));
    }

    @DeleteMapping("/api/studio/assets/{id}")
    @Operation(summary = "Удалить файл из библиотеки", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<Void> deleteAsset(@PathVariable UUID id) {
        studio.deleteAsset(currentUser.require(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/studio/templates")
    @Operation(summary = "Шаблоны монтажа", security = @SecurityRequirement(name = "Bearer"))
    public List<StudioTemplateDTO> templates() {
        return studio.templates(currentUser.require());
    }

    @PostMapping("/api/studio/templates")
    @Operation(summary = "Новый шаблон", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<StudioTemplateDTO> createTemplate(@Valid @RequestBody StudioTemplateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studio.saveTemplate(currentUser.require(), null, request));
    }

    @PutMapping("/api/studio/templates/{id}")
    @Operation(summary = "Изменить шаблон", security = @SecurityRequirement(name = "Bearer"))
    public StudioTemplateDTO updateTemplate(@PathVariable UUID id, @Valid @RequestBody StudioTemplateRequestDTO request) {
        return studio.saveTemplate(currentUser.require(), id, request);
    }

    @DeleteMapping("/api/studio/templates/{id}")
    @Operation(summary = "Удалить шаблон", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id) {
        studio.deleteTemplate(currentUser.require(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/studio/templates/active")
    @Operation(summary = "Автошаблон или избранный шаблон для Telegram",
            description = "templateId=null — выключить; favorite=true — избранный (первым в списке под видео)",
            security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<Void> setActive(@RequestBody StudioActiveTemplateRequestDTO request) {
        studio.setActiveTemplate(currentUser.require(), request.templateId(), Boolean.TRUE.equals(request.favorite()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/studio/telegram/link")
    @Operation(summary = "Ссылка для привязки Telegram", description = "Одноразовая, действует 10 минут",
            security = @SecurityRequirement(name = "Bearer"))
    public StudioTelegramLinkDTO link() {
        return studio.createTelegramLink(currentUser.require());
    }

    @DeleteMapping("/api/studio/telegram/link")
    @Operation(summary = "Отвязать Telegram", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<Void> unlink() {
        studio.unlinkTelegram(currentUser.require());
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/api/public/studio/presets/{id}.jpg", produces = MediaType.IMAGE_JPEG_VALUE)
    @Operation(summary = "Картинка встроенного фона")
    public ResponseEntity<byte[]> preset(@PathVariable String id) throws IOException {
        if (!StudioSettings.PRESETS.contains(id)) {
            return ResponseEntity.notFound().build();
        }
        byte[] body = new ClassPathResource("studio/presets/" + id + ".jpg").getContentAsByteArray();
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic()).body(body);
    }

    @ExceptionHandler(StudioException.class)
    public ResponseEntity<Map<String, String>> handleStudio(StudioException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Ошибка запроса" : e.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of("message", message.isBlank() ? "Некорректный запрос" : message));
    }
}
