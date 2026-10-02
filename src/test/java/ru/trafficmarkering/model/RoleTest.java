package ru.trafficmarkering.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    void superAdminImpliesEveryHumanRole() {
        assertThat(Role.SUPER_ADMIN.implies(Role.ADMIN)).isTrue();
        assertThat(Role.SUPER_ADMIN.implies(Role.FINANCE_MANAGER)).isTrue();
        assertThat(Role.SUPER_ADMIN.implies(Role.CUSTOMER)).isTrue();
        assertThat(Role.SUPER_ADMIN.implies(Role.CREATOR)).isTrue();
        assertThat(Role.SUPER_ADMIN.implies(Role.SERVICE)).isFalse();
    }

    @Test
    void adminImpliesCustomerAndCreatorButNotFinance() {
        assertThat(Role.ADMIN.implies(Role.CUSTOMER)).isTrue();
        assertThat(Role.ADMIN.implies(Role.CREATOR)).isTrue();
        assertThat(Role.ADMIN.implies(Role.FINANCE_MANAGER)).isFalse();
        assertThat(Role.ADMIN.implies(Role.SUPER_ADMIN)).isFalse();
    }

    @Test
    void plainRolesImplyOnlyThemselves() {
        assertThat(Role.FINANCE_MANAGER.implied()).containsExactly(Role.FINANCE_MANAGER);
        assertThat(Role.CUSTOMER.implies(Role.CREATOR)).isFalse();
        assertThat(Role.CUSTOMER.isAdmin()).isFalse();
        assertThat(Role.SUPER_ADMIN.isAdmin()).isTrue();
    }

    @Test
    void superAdminTabsAreOnlyPlatformManagement() {
        assertThat(Role.SUPER_ADMIN.tabs()).containsExactly(
                CabinetTab.OVERVIEW,
                CabinetTab.CUSTOMER_WALLETS,
                CabinetTab.TOP_UPS,
                CabinetTab.PAYOUTS,
                CabinetTab.OPERATIONS,
                CabinetTab.MODERATION,
                CabinetTab.FRAUD,
                CabinetTab.CREATOR_TRUST,
                CabinetTab.USERS);
    }

    @Test
    void usersTabBelongsOnlyToSuperAdmin() {
        for (Role role : Role.values()) {
            assertThat(role.tabs().contains(CabinetTab.USERS)).isEqualTo(role == Role.SUPER_ADMIN);
        }
    }

    @Test
    void everyHumanRoleStartsWithOverview() {
        for (Role role : Role.values()) {
            if (role == Role.SERVICE) {
                assertThat(role.tabs()).isEmpty();
            } else {
                assertThat(role.tabs()).first().isEqualTo(CabinetTab.OVERVIEW);
            }
        }
    }

    @Test
    void customerAndCreatorSeeOnlyTheirOwnCabinet() {
        assertThat(Role.CUSTOMER.tabs()).containsExactly(
                CabinetTab.OVERVIEW, CabinetTab.CAMPAIGNS, CabinetTab.WALLET, CabinetTab.PROFILE);
        assertThat(Role.CREATOR.tabs()).containsExactly(
                CabinetTab.OVERVIEW, CabinetTab.BOARD, CabinetTab.APPLICATIONS, CabinetTab.EARNINGS,
                CabinetTab.PROFILE, CabinetTab.SOCIALS);
    }

    @Test
    void superAdminAndServiceAreNeverAssignable() {
        assertThat(Role.assignable()).doesNotContain(Role.SUPER_ADMIN, Role.SERVICE);
        assertThat(Role.selfRegistrable()).containsExactlyInAnyOrder(Role.CUSTOMER, Role.CREATOR);
    }
}
