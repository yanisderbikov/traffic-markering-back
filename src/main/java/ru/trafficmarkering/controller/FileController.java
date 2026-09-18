package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.file.PresignUploadRequestDTO;
import ru.trafficmarkering.dto.file.PresignUploadResponseDTO;
import ru.trafficmarkering.service.storage.FileStorage;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File", description = "Прямая загрузка файлов в хранилище по presigned-ссылкам")
public class FileController {

    public static final String CAMPAIGN_PHOTO_PREFIX = "campaign-photos";
    public static final String CAMPAIGN_MATERIAL_PREFIX = "campaign-materials";
    public static final String TRANSFER_PROOF_PREFIX = "transfer-proofs";

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> SCRIPTABLE_MATERIAL_TYPES = Set.of(
            "text/html", "application/xhtml+xml", "image/svg+xml",
            "text/javascript", "application/javascript", "application/x-javascript");

    private final FileStorage fileStorage;

    @Operation(summary = "Ссылка на загрузку фото объявления",
            description = "Возвращает presigned PUT-ссылку: файл отправляется в хранилище напрямую, "
                    + "с тем же Content-Type, что в запросе. Полученный key передаётся при сохранении объявления",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/campaign-photo/presign")
    public ResponseEntity<PresignUploadResponseDTO> presignCampaignPhoto(
            @Valid @RequestBody PresignUploadRequestDTO request) {
        if (!ALLOWED_IMAGE_TYPES.contains(request.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Можно загрузить только изображение: JPEG, PNG, WebP или GIF");
        }
        FileStorage.PresignedUpload presigned = fileStorage.presignUpload(
                request.getFilename(), request.getContentType(), CAMPAIGN_PHOTO_PREFIX);
        return ResponseEntity.ok(new PresignUploadResponseDTO(presigned.uploadUrl(), presigned.key()));
    }

    @Operation(summary = "Ссылка на загрузку скриншота перевода",
            description = "Для менеджера финансов: presigned PUT-ссылка на скриншот перевода USDT. "
                    + "Полученный key передаётся в пополнение, вывод заказчику или отправку выплаты криатору",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/transfer-proof/presign")
    public ResponseEntity<PresignUploadResponseDTO> presignTransferProof(
            @Valid @RequestBody PresignUploadRequestDTO request) {
        if (!ALLOWED_IMAGE_TYPES.contains(request.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Можно загрузить только изображение: JPEG, PNG, WebP или GIF");
        }
        FileStorage.PresignedUpload presigned = fileStorage.presignUpload(
                request.getFilename(), request.getContentType(), TRANSFER_PROOF_PREFIX);
        return ResponseEntity.ok(new PresignUploadResponseDTO(presigned.uploadUrl(), presigned.key()));
    }

    @Operation(summary = "Ссылка на загрузку материала объявления",
            description = "Presigned PUT-ссылка на файл для криатора: бриф, баннер, референсы. "
                    + "Полученный key передаётся в materials при сохранении объявления вместе с именем, типом и размером файла",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/campaign-material/presign")
    public ResponseEntity<PresignUploadResponseDTO> presignCampaignMaterial(
            @Valid @RequestBody PresignUploadRequestDTO request) {
        if (SCRIPTABLE_MATERIAL_TYPES.contains(request.getContentType().toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Страницы, скрипты и SVG в материалы не принимаются — заархивируйте их");
        }
        FileStorage.PresignedUpload presigned = fileStorage.presignUpload(
                request.getFilename(), request.getContentType(), CAMPAIGN_MATERIAL_PREFIX);
        return ResponseEntity.ok(new PresignUploadResponseDTO(presigned.uploadUrl(), presigned.key()));
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
