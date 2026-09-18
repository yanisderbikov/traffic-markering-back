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
    void superAdminAndServiceAreNeverAssignable() {
        assertThat(Role.assignable()).doesNotContain(Role.SUPER_ADMIN, Role.SERVICE);
        assertThat(Role.selfRegistrable()).containsExactlyInAnyOrder(Role.CUSTOMER, Role.CREATOR);
    }
}
