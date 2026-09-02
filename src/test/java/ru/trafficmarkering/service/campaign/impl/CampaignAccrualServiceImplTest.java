package ru.trafficmarkering.service.campaign.impl;

import org.junit.jupiter.api.Test;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.repository.SaverCampaign;
import ru.trafficmarkering.service.campaign.CampaignAccrualService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampaignAccrualServiceImplTest {

    private final GetterApplication getterApplication = mock(GetterApplication.class);
    private final SaverApplication saverApplication = mock(SaverApplication.class);
    private final SaverCampaign saverCampaign = mock(SaverCampaign.class);

    private final CampaignAccrualService service =
            new CampaignAccrualServiceImpl(getterApplication, saverApplication, saverCampaign);

    @Test
    void recalculate_cutsLastAccrualByRemainingBudget() {
        // Бюджет 1 000 ₽, ставка 350 ₽ за тысячу: первому хватает целиком, второму — только остаток
        Campaign campaign = campaign(350_00, 1_000_00);
        Application first = application(ApplicationStatus.APPROVED, 2_000, 0);
        Application second = application(ApplicationStatus.APPROVED, 2_000, 0);
        when(getterApplication.getByCampaignIdOrderByCreatedAt(campaign.getId()))
                .thenReturn(List.of(first, second));

        service.recalculate(campaign);

        assertEquals(700_00L, first.getAccruedKopecks().longValue());
        assertEquals(300_00L, second.getAccruedKopecks().longValue());
        assertEquals(1_000_00L, campaign.getSpentKopecks().longValue());
    }

    @Test
    void recalculate_zeroesNotApprovedApplications() {
        // Отклик успели одобрить и начислить 1 750 ₽, а потом отклонили — сумма должна уйти
        Campaign campaign = campaign(350_00, 10_000_00);
        Application rejected = application(ApplicationStatus.REJECTED, 5_000, 1_750_00);
        Application pending = application(ApplicationStatus.PENDING, 3_000, 0);
        Application approved = application(ApplicationStatus.APPROVED, 1_000, 0);
        when(getterApplication.getByCampaignIdOrderByCreatedAt(campaign.getId()))
                .thenReturn(List.of(rejected, pending, approved));

        service.recalculate(campaign);

        assertEquals(0L, rejected.getAccruedKopecks().longValue());
        assertEquals(0L, pending.getAccruedKopecks().longValue());
        assertEquals(350_00L, approved.getAccruedKopecks().longValue());
        assertEquals(350_00L, campaign.getSpentKopecks().longValue());
        verify(saverApplication).save(rejected);
        // У неодобренного и так был ноль — лишний UPDATE не нужен
        verify(saverApplication, never()).save(pending);
    }

    @Test
    void recalculate_spentIsSumOfAccruals() {
        // COMPLETED начисляется наравне с APPROVED: работа сдана и оплачена
        Campaign campaign = campaign(350_00, 100_000_00);
        Application approved = application(ApplicationStatus.APPROVED, 1_000, 0);
        Application completed = application(ApplicationStatus.COMPLETED, 2_000, 0);
        when(getterApplication.getByCampaignIdOrderByCreatedAt(campaign.getId()))
                .thenReturn(List.of(approved, completed));

        service.recalculate(campaign);

        assertEquals(350_00L + 700_00L, campaign.getSpentKopecks().longValue());
        verify(saverCampaign).save(campaign);
    }

    @Test
    void recalculate_withoutApplicationsResetsSpent() {
        // Единственный отклик удалили — потраченным ничего остаться не должно
        Campaign campaign = campaign(350_00, 1_000_00);
        campaign.setSpentKopecks(700_00L);
        when(getterApplication.getByCampaignIdOrderByCreatedAt(campaign.getId())).thenReturn(List.of());

        service.recalculate(campaign);

        assertEquals(0L, campaign.getSpentKopecks().longValue());
        verify(saverCampaign).save(campaign);
    }

    private Campaign campaign(long ratePerThousandKopecks, long budgetKopecks) {
        return Campaign.builder()
                .id(UUID.randomUUID())
                .ratePerThousandKopecks(ratePerThousandKopecks)
                .budgetKopecks(budgetKopecks)
                .spentKopecks(0L)
                .build();
    }

    private Application application(ApplicationStatus status, long views, long accruedKopecks) {
        return Application.builder()
                .id(UUID.randomUUID())
                .status(status)
                .views(views)
                .accruedKopecks(accruedKopecks)
                .build();
    }
}
