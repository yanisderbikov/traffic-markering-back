package ru.trafficmarkering.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.campaign.CampaignBoardDTO;
import ru.trafficmarkering.dto.campaign.CampaignDTO;
import ru.trafficmarkering.dto.profile.CreatorProfileDTO;
import ru.trafficmarkering.service.campaign.CampaignBoardService;
import ru.trafficmarkering.service.profile.CreatorProfileService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "PublicBoard", description = "Публичная доска объявлений: доступна без авторизации")
public class PublicBoardController {

    private final CampaignBoardService campaignBoardService;
    private final CreatorProfileService creatorProfileService;

    @Operation(summary = "Доска объявлений",
            description = "Только объявления в статусе ACTIVE, новые сверху; описание урезано до 180 символов")
    @GetMapping("/campaigns")
    public ResponseEntity<List<CampaignBoardDTO>> boardCampaigns() {
        return ResponseEntity.ok(campaignBoardService.getBoard());
    }

    @Operation(summary = "Объявление по публичному номеру",
            description = "Полная карточка объявления для страницы отклика; суммы в копейках")
    @GetMapping("/campaigns/{publicId}")
    public ResponseEntity<CampaignDTO> boardCampaign(@PathVariable("publicId") String publicId) {
        return ResponseEntity.ok(campaignBoardService.getByPublicId(publicId));
    }

    @Operation(summary = "Профиль криатора",
            description = "Витрина криатора: отображаемое имя, «о себе» и соцсети — заказчик смотрит, кому отдаёт заказ")
    @GetMapping("/creators/{userId}")
    public ResponseEntity<CreatorProfileDTO> publicCreator(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(creatorProfileService.getPublicByUserId(userId));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Ошибка запроса" : e.getReason()));
    }
}
