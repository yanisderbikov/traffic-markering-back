package ru.trafficmarkering.service.campaign.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.repository.SaverCampaign;
import ru.trafficmarkering.service.campaign.CampaignAccrualService;
import ru.trafficmarkering.util.PayoutCalculator;

import java.util.Objects;

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

    @Override
    @Transactional
    public void recalculate(Campaign campaign) {
        long budget = campaign.getBudgetKopecks() != null ? campaign.getBudgetKopecks() : 0L;
        long rate = campaign.getRatePerThousandKopecks() != null ? campaign.getRatePerThousandKopecks() : 0L;
        long spent = 0L;

        // Порядок важен: бюджет достаётся тем, кто откликнулся раньше
        for (Application application : getterApplication.getByCampaignIdOrderByCreatedAt(campaign.getId())) {
            long accrued = 0L;
            if (application.isAccruable()) {
                long views = application.getViews() != null ? application.getViews() : 0L;
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
}
