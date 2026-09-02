package ru.trafficmarkering.service.campaign.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.campaign.CampaignCreateUpdateRequestDTO;
import ru.trafficmarkering.dto.campaign.CampaignDTO;
import ru.trafficmarkering.dto.campaign.CampaignStatusUpdateRequestDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.CampaignStatus;
import ru.trafficmarkering.model.profile.CustomerProfile;
import ru.trafficmarkering.repository.CampaignDeleter;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterCampaign;
import ru.trafficmarkering.repository.GetterCustomerProfile;
import ru.trafficmarkering.repository.SaverCampaign;
import ru.trafficmarkering.service.application.ApplicationService;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.controller.FileController;
import ru.trafficmarkering.service.campaign.CampaignAccrualService;
import ru.trafficmarkering.service.campaign.CampaignService;
import ru.trafficmarkering.service.storage.FileStorage;
import ru.trafficmarkering.util.PublicIdGenerator;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CampaignServiceImpl implements CampaignService {

    private final GetterCampaign getterCampaign;
    private final SaverCampaign saverCampaign;
    private final CampaignDeleter campaignDeleter;
    private final GetterApplication getterApplication;
    private final GetterCustomerProfile getterCustomerProfile;
    private final CurrentUserService currentUserService;
    private final CampaignAccrualService campaignAccrualService;
    private final ApplicationService applicationService;
    private final FileStorage fileStorage;

    @Override
    @Transactional(readOnly = true)
    public List<CampaignDTO> getMyCampaigns() {
        User customer = currentUserService.require();
        // Все объявления списка принадлежат одному заказчику — профиль достаём один раз
        CustomerProfile profile = customerProfile(customer);
        return getterCampaign.getByCustomerId(customer.getId()).stream()
                .map(campaign -> toDTO(campaign, profile))
                .toList();
    }

    @Override
    @Transactional
    public CampaignDTO create(CampaignCreateUpdateRequestDTO request) {
        User customer = currentUserService.require();
        Campaign campaign = Campaign.builder()
                .publicId(PublicIdGenerator.generateUnique(getterCampaign::existsByPublicId))
                .customer(customer)
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .photoKey(requireValidPhotoKey(request.getPhotoKey()))
                .ratePerThousandKopecks(request.getRatePerThousandKopecks())
                .budgetKopecks(request.getBudgetKopecks())
                .spentKopecks(0L)
                // null в запросе — объявление создаётся черновиком и на доску не попадает
                .status(request.getStatus() != null ? request.getStatus() : CampaignStatus.DRAFT)
                .build();
        Campaign saved = saverCampaign.save(campaign);
        return toDTO(saved, customerProfile(customer));
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignDTO getById(UUID id) {
        Campaign campaign = requireAccessible(id);
        return toDTO(campaign, customerProfile(campaign.getCustomer()));
    }

    @Override
    @Transactional
    public CampaignDTO update(UUID id, CampaignCreateUpdateRequestDTO request) {
        Campaign campaign = requireAccessible(id);
        campaign.setTitle(request.getTitle().trim());
        campaign.setDescription(request.getDescription().trim());
        campaign.setPhotoKey(requireValidPhotoKey(request.getPhotoKey()));
        campaign.setRatePerThousandKopecks(request.getRatePerThousandKopecks());
        campaign.setBudgetKopecks(request.getBudgetKopecks());
        if (request.getStatus() != null) {
            campaign.setStatus(request.getStatus());
        }
        Campaign saved = saverCampaign.save(campaign);
        // Ставка и бюджет только что могли поменяться — старые начисления им уже не соответствуют
        campaignAccrualService.recalculate(saved);
        return toDTO(saved, customerProfile(saved.getCustomer()));
    }

    @Override
    @Transactional
    public CampaignDTO updateStatus(UUID id, CampaignStatusUpdateRequestDTO request) {
        Campaign campaign = requireAccessible(id);
        campaign.setStatus(request.getStatus());
        Campaign saved = saverCampaign.save(campaign);
        return toDTO(saved, customerProfile(saved.getCustomer()));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Campaign campaign = requireAccessible(id);
        if (getterApplication.countByCampaignId(campaign.getId()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "По объявлению уже есть отклики — его нельзя удалить. Переведите его в статус «завершено».");
        }
        campaignDeleter.deleteById(campaign.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationDTO> getApplications(UUID campaignId) {
        User user = currentUserService.require();
        // Сборку DTO отклика не дублируем: она живёт в сервисе откликов.
        // Админу владельца не проверяем — ему открыты чужие объявления
        Long ownerId = user.getRole() == Role.ADMIN ? null : user.getId();
        return applicationService.getByCampaignId(campaignId, ownerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Campaign requireOwned(UUID campaignId, Long customerId) {
        Campaign campaign = getterCampaign.getById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Объявление не найдено: " + campaignId));
        User owner = campaign.getCustomer();
        if (owner == null || !owner.getId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это чужое объявление");
        }
        return campaign;
    }

    /** Админ ходит по чужим объявлениям как по своим — остальным нужно быть владельцем. */
    private Campaign requireAccessible(UUID campaignId) {
        User user = currentUserService.require();
        if (user.getRole() == Role.ADMIN) {
            return getterCampaign.getById(campaignId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Объявление не найдено: " + campaignId));
        }
        return requireOwned(campaignId, user.getId());
    }

    /**
     * Счётчик откликов и просмотры берём из одного списка: сумму просмотров
     * по одобренным откликам всё равно негде взять, кроме как из самих откликов.
     */
    private CampaignDTO toDTO(Campaign campaign, CustomerProfile customerProfile) {
        List<Application> applications = getterApplication.getByCampaignIdOrderByCreatedAt(campaign.getId());
        long totalViews = applications.stream()
                .filter(Application::isAccruable)
                .mapToLong(application -> application.getViews() != null ? application.getViews() : 0L)
                .sum();
        return CampaignDTO.from(campaign, campaign.getCustomer(), customerProfile,
                fileStorage.presignedUrl(campaign.getPhotoKey()), applications.size(), totalViews);
    }

    private String requireValidPhotoKey(String photoKey) {
        String key = photoKey.trim();
        if (!key.startsWith(FileController.CAMPAIGN_PHOTO_PREFIX + "/") || key.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ключ фотографии не из загрузки объявлений: " + key);
        }
        return key;
    }

    /** Профиль может быть не заполнен — в DTO тогда просто не будет компании. */
    private CustomerProfile customerProfile(User customer) {
        return customer != null
                ? getterCustomerProfile.getByUserId(customer.getId()).orElse(null)
                : null;
    }
}
