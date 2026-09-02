package ru.trafficmarkering.service.campaign;

import ru.trafficmarkering.dto.campaign.CampaignBoardDTO;
import ru.trafficmarkering.dto.campaign.CampaignDTO;

import java.util.List;

/**
 * Публичная доска объявлений: её читают без токена, поэтому наружу
 * не уходит ничего, кроме имени заказчика и компании из его профиля.
 */
public interface CampaignBoardService {

    /** Карточки доски: только ACTIVE, новые сверху. */
    List<CampaignBoardDTO> getBoard();

    /** Объявление целиком по короткому публичному номеру. */
    CampaignDTO getByPublicId(String publicId);
}
