package ru.trafficmarkering.service.partner.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.config.CommissionProperties;
import ru.trafficmarkering.dto.partner.PartnerDTO;
import ru.trafficmarkering.dto.partner.PartnerInviteDTO;
import ru.trafficmarkering.dto.partner.PartnerReferralDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.partner.Partner;
import ru.trafficmarkering.model.partner.ReferralReward;
import ru.trafficmarkering.model.profile.CustomerProfile;
import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.model.wallet.WalletTransactionType;
import ru.trafficmarkering.repository.GetterCustomerProfile;
import ru.trafficmarkering.repository.GetterPartner;
import ru.trafficmarkering.repository.GetterReferralReward;
import ru.trafficmarkering.repository.SaverPartner;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.auth.CurrentUserService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartnerServiceImplTest {

    private final GetterPartner getterPartner = mock(GetterPartner.class);
    private final SaverPartner saverPartner = mock(SaverPartner.class);
    private final GetterReferralReward getterReferralReward = mock(GetterReferralReward.class);
    private final GetterCustomerProfile getterCustomerProfile = mock(GetterCustomerProfile.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);

    private final PartnerServiceImpl service = new PartnerServiceImpl(getterPartner, saverPartner,
            getterReferralReward, getterCustomerProfile, userRepository, currentUserService, new CommissionProperties());

    private final User customer = User.builder().id(1L).username("partner@traffic.ru").name("Партнёр")
            .role(Role.CUSTOMER).build();
    private final Partner partner = Partner.builder().id(5L).user(customer).code("K7Q2M9XA")
            .createdAt(Instant.parse("2026-09-01T10:00:00Z")).build();

    @BeforeEach
    void setUp() {
        when(currentUserService.require(Role.CUSTOMER)).thenReturn(customer);
        when(saverPartner.save(any(Partner.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User friend(long id, String name) {
        return User.builder().id(id).username(name + "@traffic.ru").name(name).role(Role.CUSTOMER)
                .referredBy(partner).verifiedAt(Instant.now()).createdAt(Instant.now()).build();
    }

    private ReferralReward reward(User referral, String publicId, long commission, long reward) {
        WalletTransaction source = WalletTransaction.builder().publicId("S" + publicId)
                .type(WalletTransactionType.TOP_UP).build();
        WalletTransaction credited = WalletTransaction.builder().publicId(publicId)
                .type(WalletTransactionType.REFERRAL_REWARD).build();
        return ReferralReward.builder().partner(partner).referral(referral).sourceTransaction(source)
                .rewardTransaction(credited).commissionKopecks(commission).rewardKopecks(reward)
                .createdAt(Instant.now()).build();
    }

    @Test
    void myWithoutPartnershipShowsOnlyTerms() {
        when(getterPartner.getByUserId(1L)).thenReturn(Optional.empty());

        PartnerDTO dto = service.my();

        assertThat(dto.active()).isFalse();
        assertThat(dto.code()).isNull();
        assertThat(dto.commissionPercent()).isEqualByComparingTo("10");
        assertThat(dto.partnerSharePercent()).isEqualByComparingTo("10");
        assertThat(dto.referrals()).isEmpty();
        assertThat(dto.rewards()).isEmpty();
        verify(userRepository, never()).findAllByReferredByIdAndVerifiedAtIsNotNullOrderByCreatedAtDesc(any());
    }

    @Test
    void activateCreatesPartnerWithShortCode() {
        when(getterPartner.getByUserId(1L)).thenReturn(Optional.empty());
        when(getterPartner.existsByCode(anyString())).thenReturn(false);

        PartnerDTO dto = service.activate();

        assertThat(dto.active()).isTrue();
        assertThat(dto.code()).matches("[A-Z0-9]{8}");
        assertThat(dto.invitedCount()).isZero();
        verify(saverPartner, times(1)).save(any(Partner.class));
    }

    @Test
    void activateTwiceKeepsTheSameCode() {
        when(getterPartner.getByUserId(1L)).thenReturn(Optional.of(partner));

        PartnerDTO dto = service.activate();

        assertThat(dto.code()).isEqualTo("K7Q2M9XA");
        verify(saverPartner, never()).save(any());
    }

    @Test
    void myCountsInvitedFriendsAndWhatTheyBrought() {
        User anna = friend(2L, "Аня");
        User boris = friend(3L, "Борис");
        when(getterPartner.getByUserId(1L)).thenReturn(Optional.of(partner));
        when(userRepository.findAllByReferredByIdAndVerifiedAtIsNotNullOrderByCreatedAtDesc(5L))
                .thenReturn(List.of(anna, boris));
        when(getterReferralReward.getByPartnerId(5L)).thenReturn(List.of(
                reward(anna, "RW000002", 1_000_00L, 100_00L),
                reward(anna, "RW000001", 4_000_00L, 400_00L)));

        PartnerDTO dto = service.my();

        assertThat(dto.active()).isTrue();
        assertThat(dto.joinedAt()).isEqualTo("2026-09-01T10:00:00Z");
        assertThat(dto.invitedCount()).isEqualTo(2);
        assertThat(dto.activeCount()).isEqualTo(1);
        assertThat(dto.earnedKopecks()).isEqualTo(500_00L);
        assertThat(dto.referrals()).extracting(PartnerReferralDTO::name).containsExactly("Аня", "Борис");
        assertThat(dto.referrals().get(0).earnedKopecks()).isEqualTo(500_00L);
        assertThat(dto.referrals().get(0).rewardsCount()).isEqualTo(2);
        assertThat(dto.referrals().get(1).earnedKopecks()).isZero();
        assertThat(dto.rewards()).hasSize(2);
        assertThat(dto.rewards().get(0).publicId()).isEqualTo("RW000002");
        assertThat(dto.rewards().get(0).referralName()).isEqualTo("Аня");
        assertThat(dto.rewards().get(0).sourceType()).isEqualTo("TOP_UP");
        assertThat(dto.rewards().get(0).commissionKopecks()).isEqualTo(1_000_00L);
        assertThat(dto.rewards().get(0).rewardKopecks()).isEqualTo(100_00L);
    }

    @Test
    void inviteShowsPartnerNameAndCompany() {
        when(getterPartner.getByCode("K7Q2M9XA")).thenReturn(Optional.of(partner));
        when(getterCustomerProfile.getByUserId(1L))
                .thenReturn(Optional.of(CustomerProfile.builder().user(customer).company(" Ромашка ").build()));

        PartnerInviteDTO dto = service.invite(" k7q2m9xa ");

        assertThat(dto.code()).isEqualTo("K7Q2M9XA");
        assertThat(dto.name()).isEqualTo("Партнёр");
        assertThat(dto.company()).isEqualTo("Ромашка");
    }

    @Test
    void inviteHidesBlankCompany() {
        when(getterPartner.getByCode("K7Q2M9XA")).thenReturn(Optional.of(partner));
        when(getterCustomerProfile.getByUserId(1L))
                .thenReturn(Optional.of(CustomerProfile.builder().user(customer).company("  ").build()));

        assertThat(service.invite("K7Q2M9XA").company()).isNull();
    }

    @Test
    void unknownInviteIsNotFound() {
        when(getterPartner.getByCode(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.invite("NOPE"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void findByCodeSkipsBlankAndOverlongCodes() {
        assertThat(service.findByCode(null)).isEmpty();
        assertThat(service.findByCode("  ")).isEmpty();
        assertThat(service.findByCode("X".repeat(Partner.CODE_MAX_LENGTH + 1))).isEmpty();
        verify(getterPartner, never()).getByCode(any());
    }

    @Test
    void onlyCustomersWithPartnershipArePartners() {
        when(getterPartner.getByUserId(1L)).thenReturn(Optional.of(partner));
        User creator = User.builder().id(1L).role(Role.CREATOR).build();

        assertThat(service.isPartner(customer)).isTrue();
        assertThat(service.isPartner(creator)).isFalse();
    }
}
