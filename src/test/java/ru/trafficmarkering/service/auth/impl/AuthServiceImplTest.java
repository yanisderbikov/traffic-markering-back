package ru.trafficmarkering.service.auth.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.auth.AuthResponseDTO;
import ru.trafficmarkering.dto.auth.RegisterRequestDTO;
import ru.trafficmarkering.model.LoginCode;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.repository.LoginCodeStore;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.auth.JwtTokenService;
import ru.trafficmarkering.service.email.EmailService;
import ru.trafficmarkering.service.user.AccountProvisioningService;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private static final String EMAIL = "creator@traffic.ru";

    private UserRepository userRepository;
    private LoginCodeStore loginCodeStore;
    private EmailService emailService;
    private JwtTokenService jwtTokenService;
    private AccountProvisioningService accountProvisioningService;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        loginCodeStore = mock(LoginCodeStore.class);
        emailService = mock(EmailService.class);
        jwtTokenService = mock(JwtTokenService.class);
        accountProvisioningService = mock(AccountProvisioningService.class);
        authService = new AuthServiceImpl(userRepository, loginCodeStore, emailService, jwtTokenService,
                mock(CurrentUserService.class), accountProvisioningService);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loginCodeStore.save(any(LoginCode.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static User verifiedUser(Role role) {
        return User.builder().id(1L).username(EMAIL).name("Аня").role(role).verifiedAt(Instant.now()).build();
    }

    private static LoginCode freshCode(String code) {
        return LoginCode.builder()
                .email(EMAIL)
                .code(code)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(10)))
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void requestCodeSendsSixDigitCodeToKnownUser() {
        when(userRepository.existsByUsername(EMAIL)).thenReturn(true);
        when(loginCodeStore.getLatestActive(EMAIL)).thenReturn(Optional.empty());

        authService.requestCode("  Creator@Traffic.RU ");

        ArgumentCaptor<LoginCode> saved = ArgumentCaptor.forClass(LoginCode.class);
        verify(loginCodeStore).invalidateAll(EMAIL);
        verify(loginCodeStore).save(saved.capture());
        assertThat(saved.getValue().getCode()).matches("\\d{6}");
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        verify(emailService).sendEmail(eq(EMAIL), eq("Код для входа: " + saved.getValue().getCode()), anyString());
    }

    @Test
    void requestCodeRejectsUnknownEmail() {
        when(userRepository.existsByUsername(EMAIL)).thenReturn(false);

        assertThatThrownBy(() -> authService.requestCode(EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void requestCodeRespectsResendCooldown() {
        when(userRepository.existsByUsername(EMAIL)).thenReturn(true);
        when(loginCodeStore.getLatestActive(EMAIL)).thenReturn(Optional.of(freshCode("123456")));

        assertThatThrownBy(() -> authService.requestCode(EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void verifyReturnsTokenAndMarksCodeUsed() {
        LoginCode code = freshCode("123456");
        when(loginCodeStore.getLatestActive(EMAIL)).thenReturn(Optional.of(code));
        when(userRepository.findByUsername(EMAIL)).thenReturn(Optional.of(verifiedUser(Role.CREATOR)));
        when(jwtTokenService.createToken(EMAIL, Role.CREATOR, "Аня")).thenReturn("jwt");

        AuthResponseDTO response = authService.verify(EMAIL, " 123456 ");

        assertThat(response.token()).isEqualTo("jwt");
        assertThat(response.role()).isEqualTo("CREATOR");
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.name()).isEqualTo("Аня");
        assertThat(code.getUsed()).isTrue();
    }

    @Test
    void verifyCountsWrongAttemptsAndBlocksAfterLimit() {
        LoginCode code = freshCode("123456");
        code.setAttempts(LoginCode.MAX_ATTEMPTS - 1);
        when(loginCodeStore.getLatestActive(EMAIL)).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> authService.verify(EMAIL, "000000"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Неверный код");
        assertThat(code.getAttempts()).isEqualTo(LoginCode.MAX_ATTEMPTS);

        assertThatThrownBy(() -> authService.verify(EMAIL, "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Слишком много попыток");
        assertThat(code.getUsed()).isFalse();
    }

    @Test
    void verifyRejectsExpiredCode() {
        LoginCode code = freshCode("123456");
        code.setExpiresAt(Instant.now().minusSeconds(1));
        when(loginCodeStore.getLatestActive(EMAIL)).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> authService.verify(EMAIL, "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Код истёк");
    }

    @Test
    void verifyMarksFreshlyRegisteredUserAsVerified() {
        User user = User.builder().id(1L).username(EMAIL).name("Аня").role(Role.CUSTOMER).build();
        when(loginCodeStore.getLatestActive(EMAIL)).thenReturn(Optional.of(freshCode("123456")));
        when(userRepository.findByUsername(EMAIL)).thenReturn(Optional.of(user));
        when(jwtTokenService.createToken(EMAIL, Role.CUSTOMER, "Аня")).thenReturn("jwt");

        authService.verify(EMAIL, "123456");

        assertThat(user.getVerifiedAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void registerCreatesUserWithProfileAndSendsCode() {
        when(userRepository.findByUsername(EMAIL)).thenReturn(Optional.empty());
        when(loginCodeStore.getLatestActive(EMAIL)).thenReturn(Optional.empty());

        authService.register(RegisterRequestDTO.builder().email(EMAIL).name(" Аня ").role(Role.CREATOR).build());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getName()).isEqualTo("Аня");
        assertThat(saved.getValue().getVerifiedAt()).isNull();
        verify(accountProvisioningService).provision(saved.getValue());
        verify(emailService).sendEmail(eq(EMAIL), anyString(), anyString());
    }

    @Test
    void registerRejectsStaffEmailEvenIfUnverified() {
        User staff = User.builder().id(7L).username(EMAIL).name("Финансист").role(Role.FINANCE_MANAGER).build();
        when(userRepository.findByUsername(EMAIL)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> authService.register(
                RegisterRequestDTO.builder().email(EMAIL).name("Аня").role(Role.CUSTOMER).build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(staff.getRole()).isEqualTo(Role.FINANCE_MANAGER);
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void registerRejectsVerifiedEmail() {
        when(userRepository.findByUsername(EMAIL)).thenReturn(Optional.of(verifiedUser(Role.CUSTOMER)));

        assertThatThrownBy(() -> authService.register(
                RegisterRequestDTO.builder().email(EMAIL).name("Аня").role(Role.CREATOR).build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void registerOverwritesUnverifiedUser() {
        User stale = User.builder().id(1L).username(EMAIL).name("Кто-то").role(Role.CUSTOMER).build();
        when(userRepository.findByUsername(EMAIL)).thenReturn(Optional.of(stale));
        when(loginCodeStore.getLatestActive(EMAIL)).thenReturn(Optional.empty());

        authService.register(RegisterRequestDTO.builder().email(EMAIL).name("Аня").role(Role.CREATOR).build());

        assertThat(stale.getName()).isEqualTo("Аня");
        assertThat(stale.getRole()).isEqualTo(Role.CREATOR);
        verify(emailService).sendEmail(eq(EMAIL), anyString(), anyString());
    }

    @Test
    void registerRejectsAdminRole() {
        assertThatThrownBy(() -> authService.register(
                RegisterRequestDTO.builder().email(EMAIL).name("Аня").role(Role.ADMIN).build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
