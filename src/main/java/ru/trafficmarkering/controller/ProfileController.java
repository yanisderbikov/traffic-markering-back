package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.file.PresignUploadRequestDTO;
import ru.trafficmarkering.dto.file.PresignUploadResponseDTO;
import ru.trafficmarkering.dto.profile.AvatarDTO;
import ru.trafficmarkering.dto.profile.AvatarRequestDTO;
import ru.trafficmarkering.dto.profile.CreatorProfileDTO;
import ru.trafficmarkering.dto.profile.CreatorProfileRequestDTO;
import ru.trafficmarkering.dto.profile.CustomerProfileDTO;
import ru.trafficmarkering.dto.profile.CustomerProfileRequestDTO;
import ru.trafficmarkering.service.profile.AvatarService;
import ru.trafficmarkering.service.profile.CreatorProfileService;
import ru.trafficmarkering.service.profile.CustomerProfileService;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Профили креатора и заказчика")
public class ProfileController {

    private final CreatorProfileService creatorProfileService;
    private final CustomerProfileService customerProfileService;
    private final AvatarService avatarService;

    @Operation(summary = "Ссылка на загрузку аватарки",
            description = "Presigned PUT-ссылка: картинка отправляется в хранилище напрямую, с тем же Content-Type. "
                    + "Полученный key передаётся в PUT /api/profile/avatar",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/avatar/presign")
    public ResponseEntity<PresignUploadResponseDTO> presignAvatar(@Valid @RequestBody PresignUploadRequestDTO request) {
        return ResponseEntity.ok(avatarService.presign(request.getFilename(), request.getContentType()));
    }

    @Operation(summary = "Сохранить аватарку",
            description = "Привязывает загруженную картинку к учётной записи; прежняя аватарка заменяется",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/avatar")
    public ResponseEntity<AvatarDTO> setAvatar(@Valid @RequestBody AvatarRequestDTO request) {
        return ResponseEntity.ok(avatarService.set(request.key()));
    }

    @Operation(summary = "Убрать аватарку",
            description = "В кабинете снова показывается первая буква имени",
            security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/avatar")
    public ResponseEntity<AvatarDTO> removeAvatar() {
        return ResponseEntity.ok(avatarService.remove());
    }

    @Operation(summary = "Мой профиль креатора",
            description = "Профиль текущего креатора; если его почему-то нет — заводится пустой, а не 404",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/creator")
    public ResponseEntity<CreatorProfileDTO> getCreatorProfile() {
        return ResponseEntity.ok(creatorProfileService.get());
    }

    @Operation(summary = "Сохранить профиль креатора",
            description = "Полное обновление полей; пустые строки сохраняются как «не заполнено». "
                    + "Ссылки на площадки видит заказчик при разборе откликов",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/creator")
    public ResponseEntity<CreatorProfileDTO> updateCreatorProfile(@Valid @RequestBody CreatorProfileRequestDTO request) {
        return ResponseEntity.ok(creatorProfileService.update(request));
    }

    @Operation(summary = "Мой профиль заказчика",
            description = "Профиль текущего заказчика; если его почему-то нет — заводится пустой, а не 404",
            security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/customer")
    public ResponseEntity<CustomerProfileDTO> getCustomerProfile() {
        return ResponseEntity.ok(customerProfileService.get());
    }

    @Operation(summary = "Сохранить профиль заказчика",
            description = "Полное обновление полей; название компании попадает на карточку объявления",
            security = @SecurityRequirement(name = "Bearer"))
    @PutMapping("/customer")
    public ResponseEntity<CustomerProfileDTO> updateCustomerProfile(@Valid @RequestBody CustomerProfileRequestDTO request) {
        return ResponseEntity.ok(customerProfileService.update(request));
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
