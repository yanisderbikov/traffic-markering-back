package ru.trafficmarkering.service.campaign.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.campaign.CampaignBoardDTO;
import ru.trafficmarkering.dto.campaign.CampaignDTO;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.profile.CustomerProfile;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterCampaign;
import ru.trafficmarkering.repository.GetterCustomerProfile;
import ru.trafficmarkering.service.campaign.CampaignBoardService;
import ru.trafficmarkering.service.storage.FileStorage;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class CampaignBoardServiceImpl implements CampaignBoardService {

    private final GetterCampaign getterCampaign;
    private final GetterApplication getterApplication;
    private final GetterCustomerProfile getterCustomerProfile;
    private final FileStorage fileStorage;

    @Override
    @Transactional(readOnly = true)
    public List<CampaignBoardDTO> getBoard() {
        List<Campaign> campaigns = getterCampaign.getActive();
        Map<Long, CustomerProfile> profiles = customerProfiles(campaigns);
        return campaigns.stream()
                .map(campaign -> CampaignBoardDTO.from(campaign,
                        campaign.getCustomer(),
                        profileOf(campaign.getCustomer(), profiles),
                        fileStorage.presignedUrl(campaign.getPhotoKey()),
                        (int) getterApplication.countByCampaignId(campaign.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignDTO getByPublicId(String publicId) {
        // Статус не фильтруем: ссылку могли сохранить, пока объявление было активным,
        // и криатору честнее увидеть карточку со статусом «на паузе», чем 404
        Campaign campaign = getterCampaign.getByPublicId(publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Объявление не найдено: " + publicId));
        List<Application> applications = getterApplication.getByCampaignIdOrderByCreatedAt(campaign.getId());
        long totalViews = applications.stream()
                .filter(Application::isAccruable)
                .mapToLong(application -> application.getViews() != null ? application.getViews() : 0L)
                .sum();
        CustomerProfile profile = campaign.getCustomer() != null
                ? getterCustomerProfile.getByUserId(campaign.getCustomer().getId()).orElse(null)
                : null;
        return CampaignDTO.from(campaign, campaign.getCustomer(), profile,
                fileStorage.presignedUrl(campaign.getPhotoKey()), applications.size(), totalViews);
    }

    /** Компании заказчиков одним запросом: на доске карточек много, а профиль у каждой свой. */
    private Map<Long, CustomerProfile> customerProfiles(List<Campaign> campaigns) {
        Set<Long> customerIds = campaigns.stream()
                .map(campaign -> userId(campaign.getCustomer()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        return getterCustomerProfile.getAllByUserIds(customerIds).stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), Function.identity()));
    }

    private CustomerProfile profileOf(User customer, Map<Long, CustomerProfile> profiles) {
        Long id = userId(customer);
        return id != null ? profiles.get(id) : null;
    }

    private Long userId(User user) {
        return user != null ? user.getId() : null;
    }
}
