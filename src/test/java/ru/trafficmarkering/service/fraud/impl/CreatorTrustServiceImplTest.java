package ru.trafficmarkering.service.fraud.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.trafficmarkering.config.FraudProperties;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.fraud.FraudStatus;
import ru.trafficmarkering.model.fraud.TrustLevel;
import ru.trafficmarkering.model.profile.CreatorProfile;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterCreatorProfile;
import ru.trafficmarkering.repository.SaverCreatorProfile;
import ru.trafficmarkering.service.fraud.CreatorTrustService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreatorTrustServiceImplTest {

    private final GetterCreatorProfile getterCreatorProfile = mock(GetterCreatorProfile.class);
    private final SaverCreatorProfile saverCreatorProfile = mock(SaverCreatorProfile.class);
    private final GetterApplication getterApplication = mock(GetterApplication.class);
    private final FraudProperties properties = new FraudProperties();

    private final CreatorTrustService service =
            new CreatorTrustServiceImpl(getterCreatorProfile, saverCreatorProfile, getterApplication, properties);

    private final User creator = User.builder().id(1L).name("Аня").role(Role.CREATOR).build();
    private final User admin = User.builder().id(9L).name("Админ").role(Role.ADMIN).build();
    private final CreatorProfile profile = CreatorProfile.builder().id(UUID.randomUUID()).user(creator).build();

    @BeforeEach
    void setUp() {
        when(getterCreatorProfile.getByUserId(1L)).thenReturn(Optional.of(profile));
        when(saverCreatorProfile.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void unknownProfileIsNewcomer() {
        assertThat(service.levelOf(42L)).isEqualTo(TrustLevel.NEW);
    }

    @Test
    void threeCleanPaidVideosPromoteToTrusted() {
        when(getterApplication.getByCreatorId(1L)).thenReturn(List.of(
                paid(FraudStatus.CLEAN), paid(FraudStatus.VERIFIED), paid(FraudStatus.CLEAN),
                unpaid(FraudStatus.CLEAN)));

        assertThat(service.refresh(1L)).isEqualTo(TrustLevel.TRUSTED);
        assertThat(profile.trustLevel()).isEqualTo(TrustLevel.TRUSTED);
        assertThat(profile.getTrustUpdatedAt()).isNotNull();
        verify(saverCreatorProfile).save(profile);
    }

    @Test
    void twoCleanPaidVideosAreNotEnough() {
        when(getterApplication.getByCreatorId(1L)).thenReturn(List.of(paid(FraudStatus.CLEAN), paid(FraudStatus.CLEAN)));

        assertThat(service.refresh(1L)).isEqualTo(TrustLevel.NEW);
        verify(saverCreatorProfile, never()).save(any());
    }

    @Test
    void confirmedFraudRestrictsAndSecondBlocks() {
        Application strike = paid(FraudStatus.FRAUD);
        strike.setFraudReviewedAt(Instant.now());
        when(getterApplication.getByCreatorId(1L)).thenReturn(List.of(
                paid(FraudStatus.CLEAN), paid(FraudStatus.CLEAN), paid(FraudStatus.CLEAN), strike));

        assertThat(service.refresh(1L)).isEqualTo(TrustLevel.RESTRICTED);

        Application second = unpaid(FraudStatus.FRAUD);
        second.setFraudReviewedAt(Instant.now());
        when(getterApplication.getByCreatorId(1L)).thenReturn(List.of(strike, second));

        assertThat(service.refresh(1L)).isEqualTo(TrustLevel.BLOCKED);
    }

    @Test
    void automaticFraudWithoutReviewIsNotAStrike() {
        Application automatic = unpaid(FraudStatus.FRAUD);
        when(getterApplication.getByCreatorId(1L)).thenReturn(List.of(automatic));

        assertThat(service.refresh(1L)).isEqualTo(TrustLevel.NEW);
        assertThat(service.stats(List.of(automatic)).suspicious()).isEqualTo(1);
        assertThat(service.stats(List.of(automatic)).strikes()).isZero();
    }

    @Test
    void manualLevelIsNotOverriddenByRefresh() {
        service.setManual(1L, TrustLevel.TRUSTED, "знаем лично", admin);
        Application strike = unpaid(FraudStatus.FRAUD);
        strike.setFraudReviewedAt(Instant.now());
        when(getterApplication.getByCreatorId(1L)).thenReturn(List.of(strike));

        assertThat(service.refresh(1L)).isEqualTo(TrustLevel.TRUSTED);
        assertThat(profile.isTrustManual()).isTrue();
        assertThat(profile.getTrustUpdatedBy()).isSameAs(admin);
        assertThat(profile.getTrustNote()).isEqualTo("знаем лично");
    }

    @Test
    void clearingManualLevelReturnsCreatorToAutomation() {
        service.setManual(1L, TrustLevel.BLOCKED, null, admin);
        when(getterApplication.getByCreatorId(1L)).thenReturn(List.of(
                paid(FraudStatus.CLEAN), paid(FraudStatus.CLEAN), paid(FraudStatus.CLEAN)));

        assertThat(service.setManual(1L, null, "разобрались", admin)).isEqualTo(TrustLevel.TRUSTED);
        assertThat(profile.isTrustManual()).isFalse();
    }

    private Application paid(FraudStatus status) {
        return Application.builder().id(UUID.randomUUID()).creator(creator).status(ApplicationStatus.COMPLETED)
                .accruedKopecks(500_00L).creditedKopecks(500_00L).fraudStatus(status).build();
    }

    private Application unpaid(FraudStatus status) {
        return Application.builder().id(UUID.randomUUID()).creator(creator).status(ApplicationStatus.APPROVED)
                .accruedKopecks(500_00L).creditedKopecks(0L).fraudStatus(status).build();
    }
}
