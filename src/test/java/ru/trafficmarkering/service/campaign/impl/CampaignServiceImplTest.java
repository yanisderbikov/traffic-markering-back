package ru.trafficmarkering.service.campaign.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.campaign.CampaignCreateUpdateRequestDTO;
import ru.trafficmarkering.dto.campaign.CampaignDTO;
import ru.trafficmarkering.dto.campaign.CampaignStatusUpdateRequestDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.CampaignStatus;
import ru.trafficmarkering.repository.CampaignDeleter;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterCampaign;
import ru.trafficmarkering.repository.GetterCustomerProfile;
import ru.trafficmarkering.repository.SaverCampaign;
import ru.trafficmarkering.service.application.ApplicationService;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.campaign.CampaignAccrualService;
import ru.trafficmarkering.service.storage.FileStorage;
import ru.trafficmarkering.service.wallet.WalletService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampaignServiceImplTest {

    private final GetterCampaign getterCampaign = mock(GetterCampaign.class);
    private final SaverCampaign saverCampaign = mock(SaverCampaign.class);
    private final CampaignDeleter campaignDeleter = mock(CampaignDeleter.class);
    private final GetterApplication getterApplication = mock(GetterApplication.class);
    private final GetterCustomerProfile getterCustomerProfile = mock(GetterCustomerProfile.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final CampaignAccrualService campaignAccrualService = mock(CampaignAccrualService.class);
    private final ApplicationService applicationService = mock(ApplicationService.class);
    private final FileStorage fileStorage = mock(FileStorage.class);
    private final WalletService walletService = mock(WalletService.class);

    private final CampaignServiceImpl service = new CampaignServiceImpl(
            getterCampaign, saverCampaign, campaignDeleter, getterApplication, getterCustomerProfile,
            currentUserService, campaignAccrualService, applicationService, fileStorage, walletService);

    private final User customer = User.builder().id(1L).username("brand@traffic.ru").name("Бренд").role(Role.CUSTOMER).build();

    @BeforeEach
    void setUp() {
        when(currentUserService.require()).thenReturn(customer);
        when(getterCustomerProfile.getByUserId(1L)).thenReturn(Optional.empty());
        when(saverCampaign.save(any(Campaign.class))).thenAnswer(inv -> {
            Campaign campaign = inv.getArgument(0);
            if (campaign.getId() == null) {
                campaign.setId(UUID.randomUUID());
            }
            return campaign;
        });
    }

    private Campaign campaign(CampaignStatus status, String title, Instant updatedAt) {
        Campaign campaign = Campaign.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .title(title)
                .status(status)
                .budgetKopecks(10_000_00L)
                .spentKopecks(0L)
                .build();
        campaign.setUpdatedAt(updatedAt);
        when(getterCampaign.getById(campaign.getId())).thenReturn(Optional.of(campaign));
        return campaign;
    }

    private void customerHas(Campaign... campaigns) {
        when(getterCampaign.getByCustomerId(1L)).thenReturn(List.of(campaigns));
    }

    @Test
    void startDraftOffersLatestUnfinishedDraftInsteadOfCreatingAnother() {
        Campaign older = campaign(CampaignStatus.DRAFT, "Старый", Instant.parse("2026-09-20T10:00:00Z"));
        Campaign latest = campaign(CampaignStatus.DRAFT, "Свежий", Instant.parse("2026-09-24T10:00:00Z"));
        customerHas(older, latest);

        CampaignDTO draft = service.startDraft(false);

        assertThat(draft.id()).isEqualTo(latest.getId());
        verify(saverCampaign, never()).save(any());
        verify(campaignDeleter, never()).deleteById(any());
    }

    @Test
    void startDraftCreatesBlankDraftWhenNoneIsUnfinished() {
        Campaign active = campaign(CampaignStatus.ACTIVE, "Идёт", Instant.now());
        Campaign draftWithApplications = campaign(CampaignStatus.DRAFT, "С откликами", Instant.now());
        when(getterApplication.countByCampaignId(draftWithApplications.getId())).thenReturn(2L);
        customerHas(active, draftWithApplications);

        CampaignDTO draft = service.startDraft(false);

        ArgumentCaptor<Campaign> saved = ArgumentCaptor.forClass(Campaign.class);
        verify(saverCampaign).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(saved.getValue().getTitle()).isNull();
        assertThat(saved.getValue().getPlatforms()).isNotEmpty();
        assertThat(draft.id()).isEqualTo(saved.getValue().getId());
        verify(campaignDeleter, never()).deleteById(any());
    }

    @Test
    void restartDeletesUnfinishedDraftsWithTheirBudgetAndCreatesBlankOne() {
        Campaign first = campaign(CampaignStatus.DRAFT, "Первый", Instant.parse("2026-09-20T10:00:00Z"));
        Campaign second = campaign(CampaignStatus.DRAFT, null, Instant.parse("2026-09-21T10:00:00Z"));
        Campaign active = campaign(CampaignStatus.ACTIVE, "Идёт", Instant.now());
        customerHas(first, second, active);

        CampaignDTO draft = service.startDraft(true);

        verify(walletService).releaseBeforeDelete(first);
        verify(walletService).releaseBeforeDelete(second);
        verify(campaignDeleter).deleteById(first.getId());
        verify(campaignDeleter).deleteById(second.getId());
        verify(campaignDeleter, never()).deleteById(active.getId());
        assertThat(draft.id()).isNotIn(first.getId(), second.getId(), active.getId());
        assertThat(draft.title()).isNull();
    }

    @Test
    void createRefusesSecondUnfinishedDraft() {
        customerHas(campaign(CampaignStatus.DRAFT, "Уже есть", Instant.now()));

        assertThatThrownBy(() -> service.create(CampaignCreateUpdateRequestDTO.builder().title("Ещё один").build()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(saverCampaign, never()).save(any());
    }

    @Test
    void launchedCampaignCannotGoBackToDraft() {
        Campaign active = campaign(CampaignStatus.ACTIVE, "Идёт", Instant.now());

        assertThatThrownBy(() -> service.updateStatus(active.getId(),
                CampaignStatusUpdateRequestDTO.builder().status(CampaignStatus.DRAFT).build()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(active.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
        verify(saverCampaign, never()).save(any());
    }

    @Test
    void draftCannotLaunchUntilFilled() {
        Campaign draft = campaign(CampaignStatus.DRAFT, "Только название", Instant.now());

        assertThatThrownBy(() -> service.updateStatus(draft.getId(),
                CampaignStatusUpdateRequestDTO.builder().status(CampaignStatus.ACTIVE).build()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains("описание", "обложка", "ставка");
                });
        verify(saverCampaign, never()).save(any());
    }
}
