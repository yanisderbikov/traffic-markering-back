package ru.trafficmarkering.service.admin.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.admin.AdminUserDTO;
import ru.trafficmarkering.dto.admin.AssignRoleRequestDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.user.AccountProvisioningService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuperAdminServiceImplTest {

    private static final String SUPER_ADMIN = "boss@traffic.ru";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final AccountProvisioningService accountProvisioningService = mock(AccountProvisioningService.class);

    private final SuperAdminServiceImpl service = new SuperAdminServiceImpl(userRepository, currentUserService,
            accountProvisioningService, " Boss@Traffic.RU ");

    @BeforeEach
    void setUp() {
        when(currentUserService.require(Role.SUPER_ADMIN))
                .thenReturn(User.builder().id(1L).username(SUPER_ADMIN).name("Босс").role(Role.SUPER_ADMIN).build());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static AssignRoleRequestDTO request(String email, String name, Role role) {
        return AssignRoleRequestDTO.builder().email(email).name(name).role(role).build();
    }

    @Test
    void assignRoleCreatesMissingUserAndProvisionsAccount() {
        when(userRepository.findByUsername("money@traffic.ru")).thenReturn(Optional.empty());

        AdminUserDTO dto = service.assignRole(request(" Money@Traffic.ru ", " Маша ", Role.FINANCE_MANAGER));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("money@traffic.ru");
        assertThat(saved.getValue().getName()).isEqualTo("Маша");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.FINANCE_MANAGER);
        assertThat(saved.getValue().getVerifiedAt()).isNull();
        verify(accountProvisioningService).provision(saved.getValue());
        assertThat(dto.role()).isEqualTo("FINANCE_MANAGER");
    }

    @Test
    void assignRoleDefaultsNameToMailboxWhenCreating() {
        when(userRepository.findByUsername("money@traffic.ru")).thenReturn(Optional.empty());

        AdminUserDTO dto = service.assignRole(request("money@traffic.ru", "  ", Role.ADMIN));

        assertThat(dto.name()).isEqualTo("money");
    }

    @Test
    void assignRoleChangesExistingUserKeepingNameUnlessGiven() {
        User existing = User.builder().id(5L).username("anna@traffic.ru").name("Аня").role(Role.CREATOR).build();
        when(userRepository.findByUsername("anna@traffic.ru")).thenReturn(Optional.of(existing));

        service.assignRole(request("anna@traffic.ru", null, Role.CUSTOMER));

        assertThat(existing.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(existing.getName()).isEqualTo("Аня");
        verify(accountProvisioningService).provision(existing);
    }

    @Test
    void assignRoleRefusesSuperAdminAndServiceRoles() {
        assertThatThrownBy(() -> service.assignRole(request("x@traffic.ru", null, Role.SUPER_ADMIN)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.assignRole(request("x@traffic.ru", null, Role.SERVICE)))
                .isInstanceOf(ResponseStatusException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void assignRoleRefusesToTouchSuperAdminFromEnv() {
        assertThatThrownBy(() -> service.assignRole(request(SUPER_ADMIN, null, Role.CUSTOMER)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("SUPER_ADMIN_EMAIL");
        verify(userRepository, never()).save(any());
    }

    @Test
    void assignRoleRequiresSuperAdmin() {
        when(currentUserService.require(Role.SUPER_ADMIN))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Раздел доступен только для роли SUPER_ADMIN"));

        assertThatThrownBy(() -> service.assignRole(request("x@traffic.ru", null, Role.ADMIN)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
