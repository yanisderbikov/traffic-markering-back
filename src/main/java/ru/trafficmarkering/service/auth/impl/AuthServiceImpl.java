package ru.trafficmarkering.service.auth.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.CurrentUserDTO;
import ru.trafficmarkering.dto.LoginRequestDTO;
import ru.trafficmarkering.dto.LoginResponseDTO;
import ru.trafficmarkering.dto.RegisterRequestDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.profile.CreatorProfile;
import ru.trafficmarkering.model.profile.CustomerProfile;
import ru.trafficmarkering.repository.SaverCreatorProfile;
import ru.trafficmarkering.repository.SaverCustomerProfile;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.auth.AuthService;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.auth.JwtTokenService;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
class AuthServiceImpl implements AuthService {

    /** Снаружи можно завести только эти роли: ADMIN раздаётся руками, SERVICE в базе не живёт */
    private static final Set<Role> SELF_REGISTRABLE = EnumSet.of(Role.CUSTOMER, Role.CREATOR);
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final CurrentUserService currentUserService;
    private final SaverCreatorProfile saverCreatorProfile;
    private final SaverCustomerProfile saverCustomerProfile;

    @Override
    @Transactional
    public LoginResponseDTO register(RegisterRequestDTO request) {
        Role role = request.getRole();
        if (role == null || !SELF_REGISTRABLE.contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Роль должна быть CUSTOMER (заказчик) или CREATOR (криатор)");
        }
        String password = request.getPassword();
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Пароль должен быть не короче " + MIN_PASSWORD_LENGTH + " символов");
        }
        String username = normalizeUsername(request.getUsername());
        if (username.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Логин обязателен");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Пользователь с таким логином уже зарегистрирован");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setName(request.getName().trim());
        user.setRole(role);
        User saved = userRepository.save(user);

        createEmptyProfile(saved);

        return new LoginResponseDTO(jwtTokenService.createToken(saved.getUsername(), saved.getRole(), saved.getName()));
    }

    @Override
    public LoginResponseDTO login(LoginRequestDTO request) {
        // Ответ одинаковый и на неизвестный логин, и на неверный пароль:
        // иначе форма входа превращается в проверялку «есть ли такой пользователь»
        User user = userRepository.findByUsername(normalizeUsername(request.getUsername()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль");
        }
        return new LoginResponseDTO(jwtTokenService.createToken(user.getUsername(), user.getRole(), user.getName()));
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserDTO me() {
        return CurrentUserDTO.from(currentUserService.require());
    }

    /**
     * Профиль заводится пустым прямо при регистрации: так все остальные сервисы
     * работают с существующей строкой, а не с «профиля ещё нет».
     */
    private void createEmptyProfile(User user) {
        if (user.getRole() == Role.CREATOR) {
            saverCreatorProfile.save(CreatorProfile.builder().user(user).build());
        } else if (user.getRole() == Role.CUSTOMER) {
            saverCustomerProfile.save(CustomerProfile.builder().user(user).build());
        }
    }

    /** Логин — это e-mail: регистр и случайные пробелы не должны мешать войти. */
    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
