package ru.trafficmarkering.service.campaign.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.config.FraudProperties;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.fraud.TrustLevel;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterCampaign;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.repository.SaverCampaign;
import ru.trafficmarkering.service.campaign.CampaignAccrualService;
import ru.trafficmarkering.service.fraud.CreatorTrustService;
import ru.trafficmarkering.util.PayoutCalculator;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Отклики читаем через репозиторий, а не через ApplicationService: пересчёт зовётся
 * из самого сервиса откликов, и зависимость через сервис замкнулась бы в цикл.
 */
@Service
@RequiredArgsConstructor
class CampaignAccrualServiceImpl implements CampaignAccrualService {

    private final GetterApplication getterApplication;
    private final SaverApplication saverApplication;
    private final SaverCampaign saverCampaign;
    private final CreatorTrustService creatorTrustService;
    private final FraudProperties fraudProperties;
    private final GetterCampaign getterCampaign;

    @Override
    @Transactional
    public void recalculate(Campaign campaign) {
        long budget = campaign.getBudgetKopecks() != null ? campaign.getBudgetKopecks() : 0L;
        long rate = campaign.getRatePerThousandKopecks() != null ? campaign.getRatePerThousandKopecks() : 0L;
        long spent = 0L;

        List<Application> applications = getterApplication.getByCampaignIdOrderByCreatedAt(campaign.getId());
        Map<Long, TrustLevel> trust = creatorTrustService.levelsOf(
                applications.stream().map(application -> application.getCreator().getId()).distinct().toList());

        // Порядок важен: бюджет достаётся тем, кто откликнулся раньше
        for (Application application : applications) {
            long accrued = 0L;
            long views = payableViews(application, campaign,
                    trust.getOrDefault(application.getCreator().getId(), TrustLevel.NEW));
            // isAccruable учитывает и статус отклика, и вердикт антифрода: за накрутку не платим
            if (application.isAccruable() && campaign.paysViews(views)) {
                accrued = PayoutCalculator.accrual(views, rate, budget - spent);
                spent += accrued;
            }
            // Неодобренным (PENDING, REJECTED) начисление обнуляем: отклик мог быть одобрен,
            // а потом отклонён — старая сумма не должна висеть на нём и в бюджете
            if (!Objects.equals(application.getAccruedKopecks(), accrued)) {
                application.setAccruedKopecks(accrued);
                saverApplication.save(application);
            }
        }

        campaign.setSpentKopecks(spent);
        saverCampaign.save(campaign);
    }

    @Override
    @Transactional
    public void recalculate(UUID campaignId) {
        getterCampaign.getById(campaignId).ifPresent(this::recalculate);
    }

    /** Новичкам и ограниченным криаторам оплачиваемые просмотры одного ролика режутся потолком. */
    private long payableViews(Application application, Campaign campaign, TrustLevel level) {
        long views = application.payableViews(campaign.viewRegion()).views();
        if (level.capsPayableViews()) {
            return Math.min(views, fraudProperties.getNewCreatorMaxPayableViews());
        }
        return views;
    }
}
