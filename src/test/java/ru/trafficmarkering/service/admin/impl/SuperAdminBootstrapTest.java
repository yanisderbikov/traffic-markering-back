package ru.trafficmarkering.service.admin.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuperAdminBootstrapTest {

    private final UserRepository userRepository = mock(UserRepository.class);

    @BeforeEach
    void setUp() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void promotesEveryEmailFromCommaSeparatedList() {
        User existing = User.builder().id(2L).username("deputy@traffic.ru").name("Зам").role(Role.ADMIN).build();
        when(userRepository.findAllByRole(Role.SUPER_ADMIN)).thenReturn(List.of());
        when(userRepository.findByUsername("boss@traffic.ru")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("deputy@traffic.ru")).thenReturn(Optional.of(existing));

        new SuperAdminBootstrap(userRepository, " Boss@Traffic.ru , deputy@traffic.ru ,, ").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(User::getUsername)
                .containsExactlyInAnyOrder("boss@traffic.ru", "deputy@traffic.ru");
        assertThat(saved.getAllValues()).allMatch(user -> user.getRole() == Role.SUPER_ADMIN);
        assertThat(existing.getName()).isEqualTo("Зам");
    }

    @Test
    void demotesSuperAdminsMissingFromList() {
        User kept = User.builder().id(1L).username("boss@traffic.ru").role(Role.SUPER_ADMIN).build();
        User stale = User.builder().id(3L).username("old@traffic.ru").role(Role.SUPER_ADMIN).build();
        when(userRepository.findAllByRole(Role.SUPER_ADMIN)).thenReturn(List.of(kept, stale));
        when(userRepository.findByUsername("boss@traffic.ru")).thenReturn(Optional.of(kept));

        new SuperAdminBootstrap(userRepository, "boss@traffic.ru").run(null);

        assertThat(kept.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(stale.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository).save(stale);
        verify(userRepository, never()).save(kept);
    }

    @Test
    void blankListLeavesUsersUntouched() {
        new SuperAdminBootstrap(userRepository, " , ").run(null);

        verify(userRepository, never()).findAllByRole(any());
        verify(userRepository, never()).save(any());
    }
}
