package ru.trafficmarkering.service.admin.impl;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.repository.UserRepository;

@Component
@Log4j2
class SuperAdminBootstrap implements ApplicationRunner {

    private static final String DEFAULT_NAME = "Супер-админ";

    private final UserRepository userRepository;
    private final String superAdminEmail;

    SuperAdminBootstrap(UserRepository userRepository,
                        @Value("${app.super-admin.email}") String superAdminEmail) {
        this.userRepository = userRepository;
        this.superAdminEmail = User.normalizeEmail(superAdminEmail);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (superAdminEmail.isEmpty()) {
            log.warn("SUPER_ADMIN_EMAIL не задан: супер-админа в системе нет");
            return;
        }
        for (User stale : userRepository.findAllByRole(Role.SUPER_ADMIN)) {
            if (!superAdminEmail.equals(stale.getUsername())) {
                stale.setRole(Role.ADMIN);
                userRepository.save(stale);
                log.warn("Супер-админ {} больше не указан в SUPER_ADMIN_EMAIL, понижен до ADMIN", stale.getUsername());
            }
        }
        User user = userRepository.findByUsername(superAdminEmail)
                .orElseGet(() -> User.builder().username(superAdminEmail).name(DEFAULT_NAME).build());
        if (user.getRole() != Role.SUPER_ADMIN) {
            user.setRole(Role.SUPER_ADMIN);
            userRepository.save(user);
            log.info("Супер-админ: {}", superAdminEmail);
        }
    }
}
