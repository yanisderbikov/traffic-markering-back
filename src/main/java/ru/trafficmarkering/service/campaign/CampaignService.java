package ru.trafficmarkering.service.campaign;

import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.campaign.CampaignCreateUpdateRequestDTO;
import ru.trafficmarkering.dto.campaign.CampaignDTO;
import ru.trafficmarkering.dto.campaign.CampaignStatusUpdateRequestDTO;
import ru.trafficmarkering.model.campaign.Campaign;

import java.util.List;
import java.util.UUID;

/**
 * Кабинет заказчика: свои объявления и отклики по ним.
 * Текущего пользователя сервис достаёт сам — контроллер про авторизацию не знает.
 */
public interface CampaignService {

    /** Объявления текущего заказчика, новые сверху. */
    List<CampaignDTO> getMyCampaigns();

    CampaignDTO create(CampaignCreateUpdateRequestDTO request);

    CampaignDTO getById(UUID id);

    CampaignDTO update(UUID id, CampaignCreateUpdateRequestDTO request);

    CampaignDTO updateStatus(UUID id, CampaignStatusUpdateRequestDTO request);

    /** Удаляет объявление; если по нему уже есть отклики — 409. */
    void delete(UUID id);

    /** Отклики по объявлению — для карточки объявления в кабинете заказчика. */
    List<ApplicationDTO> getApplications(UUID campaignId);

    /**
     * Объявление, которым владеет заказчик: 404 — нет такого, 403 — чужое.
     * Нужна и сервису откликов: заказчик может трогать отклики только на своих объявлениях.
     */
    Campaign requireOwned(UUID campaignId, Long customerId);
}
