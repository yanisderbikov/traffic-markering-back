package ru.trafficmarkering.service.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.CurrentUserDTO;
import ru.trafficmarkering.dto.auth.AuthResponseDTO;
import ru.trafficmarkering.dto.auth.RegisterRequestDTO;
import ru.trafficmarkering.model.LoginCode;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.repository.LoginCodeStore;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.auth.AuthService;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.auth.JwtTokenService;
import ru.trafficmarkering.service.email.EmailService;
import ru.trafficmarkering.service.email.EmailTemplate;
import ru.trafficmarkering.service.user.AccountProvisioningService;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Log4j2
class AuthServiceImpl implements AuthService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final LoginCodeStore loginCodeStore;
    private final EmailService emailService;
    private final JwtTokenService jwtTokenService;
    private final CurrentUserService currentUserService;
    private final AccountProvisioningService accountProvisioningService;

    @Override
    @Transactional
    public void register(RegisterRequestDTO request) {
        Role role = request.getRole();
        if (role == null || !Role.selfRegistrable().contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Роль должна быть CUSTOMER (заказчик) или CREATOR (криатор)");
        }
        String email = User.normalizeEmail(request.getEmail());
        String name = request.getName().trim();

        User user = userRepository.findByUsername(email).orElse(null);
        if (user != null && user.getVerifiedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Пользователь с такой почтой уже зарегистрирован. Войдите по коду.");
        }
        if (user != null && !Role.selfRegistrable().contains(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Эта почта закреплена за служебной учётной записью. Войдите по коду.");
        }
        if (user == null) {
            user = User.builder().username(email).build();
        }
        user.setName(name);
        user.setRole(role);
        User saved = userRepository.save(user);
        accountProvisioningService.provision(saved);

        sendCode(email);
    }

    @Override
    @Transactional
    public void requestCode(String rawEmail) {
        String email = User.normalizeEmail(rawEmail);
        if (!userRepository.existsByUsername(email)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Аккаунт с этой почтой не найден. Зарегистрируйтесь.");
        }
        sendCode(email);
    }

    @Override
    @Transactional
    public AuthResponseDTO verify(String rawEmail, String rawCode) {
        String email = User.normalizeEmail(rawEmail);
        String code = rawCode == null ? "" : rawCode.trim();

        LoginCode loginCode = loginCodeStore.getLatestActive(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Код не найден. Запросите новый."));

        if (loginCode.isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Код истёк. Запросите новый.");
        }
        if (loginCode.getAttempts() >= LoginCode.MAX_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Слишком много попыток. Запросите новый код.");
        }
        if (!loginCode.getCode().equals(code)) {
            loginCode.setAttempts(loginCode.getAttempts() + 1);
            loginCodeStore.save(loginCode);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный код. Проверьте письмо.");
        }

        loginCode.setUsed(true);
        loginCodeStore.save(loginCode);

        User user = userRepository.findByUsername(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Аккаунт с этой почтой не найден. Зарегистрируйтесь."));
        if (user.getVerifiedAt() == null) {
            user.setVerifiedAt(Instant.now());
            user = userRepository.save(user);
        }

        String token = jwtTokenService.createToken(user.getUsername(), user.getRole(), user.getName());
        return new AuthResponseDTO(token, user.getRole().name(), user.getUsername(), user.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserDTO me() {
        return CurrentUserDTO.from(currentUserService.require());
    }

    private void sendCode(String email) {
        var existing = loginCodeStore.getLatestActive(email);
        if (existing.isPresent() && !existing.get().isExpired()
                && existing.get().getCreatedAt() != null
                && existing.get().getCreatedAt().isAfter(Instant.now().minus(RESEND_COOLDOWN))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Код уже отправлен. Проверьте почту или запросите новый через 30 секунд.");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        loginCodeStore.invalidateAll(email);
        loginCodeStore.save(LoginCode.builder()
                .email(email)
                .code(code)
                .expiresAt(Instant.now().plus(CODE_TTL))
                .build());

        emailService.sendEmail(email, "Код для входа: " + code, EmailTemplate.getLoginCodeEmail(code));
        log.info("Код входа отправлен на {}", email);
    }
}
