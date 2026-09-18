package ru.trafficmarkering.service.campaign.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.campaign.CampaignCreateUpdateRequestDTO;
import ru.trafficmarkering.dto.campaign.CampaignDTO;
import ru.trafficmarkering.dto.campaign.CampaignMaterialDTO;
import ru.trafficmarkering.dto.campaign.CampaignMaterialRequestDTO;
import ru.trafficmarkering.dto.campaign.CampaignStatusUpdateRequestDTO;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.CampaignMaterial;
import ru.trafficmarkering.model.campaign.CampaignStatus;
import ru.trafficmarkering.model.campaign.MaterialKind;
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
import ru.trafficmarkering.service.wallet.WalletService;
import ru.trafficmarkering.util.MoneyUtil;
import ru.trafficmarkering.util.PublicIdGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final WalletService walletService;

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
                .minPayoutKopecks(request.getMinPayoutKopecks())
                .platforms(requireAcceptingVideos(request.getPlatforms()))
                .viewRegion(request.getViewRegion())
                .minVideoSeconds(request.getMinVideoSeconds())
                .minPaidViews(request.getMinPaidViews())
                .maxVideosPerCreator(request.getMaxVideosPerCreator())
                .startsAt(request.getStartsAt())
                .endsAt(requireEndAfterStart(request.getStartsAt(), request.getEndsAt()))
                .materials(toMaterials(request.getMaterials()))
                .spentKopecks(0L)
                // null в запросе — объявление создаётся черновиком и на доску не попадает
                .status(request.getStatus() != null ? request.getStatus() : CampaignStatus.DRAFT)
                .build();
        Campaign saved = saverCampaign.save(campaign);
        walletService.reallocate(saved, 0L, saved.getBudgetKopecks());
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
        long previousBudget = campaign.getBudgetKopecks() != null ? campaign.getBudgetKopecks() : 0L;
        long nextBudget = requireBudgetCoversSpent(campaign, request.getBudgetKopecks());
        campaign.setTitle(request.getTitle().trim());
        campaign.setDescription(request.getDescription().trim());
        campaign.setPhotoKey(requireValidPhotoKey(request.getPhotoKey()));
        campaign.setRatePerThousandKopecks(request.getRatePerThousandKopecks());
        campaign.setBudgetKopecks(nextBudget);
        campaign.setMinPayoutKopecks(request.getMinPayoutKopecks());
        campaign.getPlatforms().clear();
        campaign.getPlatforms().addAll(requireAcceptingVideos(request.getPlatforms()));
        campaign.setViewRegion(request.getViewRegion());
        campaign.setMinVideoSeconds(request.getMinVideoSeconds());
        campaign.setMinPaidViews(request.getMinPaidViews());
        campaign.setMaxVideosPerCreator(request.getMaxVideosPerCreator());
        campaign.setStartsAt(request.getStartsAt());
        campaign.setEndsAt(requireEndAfterStart(request.getStartsAt(), request.getEndsAt()));
        campaign.getMaterials().clear();
        campaign.getMaterials().addAll(toMaterials(request.getMaterials()));
        if (request.getStatus() != null) {
            campaign.setStatus(request.getStatus());
        }
        Campaign saved = saverCampaign.save(campaign);
        walletService.reallocate(saved, previousBudget, nextBudget);
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
        walletService.releaseBeforeDelete(campaign);
        campaignDeleter.deleteById(campaign.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationDTO> getApplications(UUID campaignId) {
        User user = currentUserService.require();
        // Сборку DTO отклика не дублируем: она живёт в сервисе откликов.
        // Админу владельца не проверяем — ему открыты чужие объявления
        Long ownerId = user.getRole().isAdmin() ? null : user.getId();
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
        if (user.getRole().isAdmin()) {
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
                fileStorage.presignedUrl(campaign.getPhotoKey()),
                CampaignMaterials.toDTO(campaign, fileStorage), applications.size(), totalViews);
    }

    private Instant requireEndAfterStart(Instant startsAt, Instant endsAt) {
        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Окончание действия объявления раньше его начала");
        }
        return endsAt;
    }

    private List<CampaignMaterial> toMaterials(List<CampaignMaterialRequestDTO> materials) {
        List<CampaignMaterial> result = new ArrayList<>();
        if (materials == null) {
            return result;
        }
        for (CampaignMaterialRequestDTO material : materials) {
            result.add(material.getKind() == MaterialKind.FILE ? fileMaterial(material) : linkMaterial(material));
        }
        return result;
    }

    private CampaignMaterial fileMaterial(CampaignMaterialRequestDTO material) {
        String key = material.getFileKey() == null ? "" : material.getFileKey().trim();
        if (!key.startsWith(FileController.CAMPAIGN_MATERIAL_PREFIX + "/") || key.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ключ файла не из загрузки материалов: " + key);
        }
        String title = trimToNull(material.getTitle());
        return CampaignMaterial.builder()
                .kind(MaterialKind.FILE)
                .title(title != null ? title : key.substring(key.lastIndexOf('/') + 1))
                .fileKey(key)
                .contentType(trimToNull(material.getContentType()))
                .sizeBytes(material.getSizeBytes())
                .build();
    }

    private CampaignMaterial linkMaterial(CampaignMaterialRequestDTO material) {
        String url = trimToNull(material.getUrl());
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ссылка на материал должна начинаться с http:// или https://");
        }
        String title = trimToNull(material.getTitle());
        return CampaignMaterial.builder()
                .kind(MaterialKind.LINK)
                .title(title != null ? title : url)
                .url(url)
                .build();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Set<Platform> requireAcceptingVideos(Set<Platform> platforms) {
        Set<Platform> accepting = Platform.acceptingVideos();
        String unsupported = platforms.stream()
                .filter(platform -> !accepting.contains(platform))
                .map(Platform::getDescription)
                .collect(Collectors.joining(", "));
        if (!unsupported.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Площадка не принимает ролики: " + unsupported);
        }
        return EnumSet.copyOf(platforms);
    }

    private long requireBudgetCoversSpent(Campaign campaign, Long budgetKopecks) {
        long spent = campaign.getSpentKopecks() != null ? campaign.getSpentKopecks() : 0L;
        if (budgetKopecks < spent) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Бюджет нельзя опустить ниже уже начисленного криаторам: " + MoneyUtil.formatRubles(spent));
        }
        return budgetKopecks;
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
