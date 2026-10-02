package ru.trafficmarkering.service.admin.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.admin.AdminUserDTO;
import ru.trafficmarkering.dto.admin.AssignRoleRequestDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.admin.SuperAdminService;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.user.AccountProvisioningService;

import java.util.List;
import java.util.stream.Collectors;

@Service
class SuperAdminServiceImpl implements SuperAdminService {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final AccountProvisioningService accountProvisioningService;
    private final String superAdminEmail;

    SuperAdminServiceImpl(UserRepository userRepository,
                          CurrentUserService currentUserService,
                          AccountProvisioningService accountProvisioningService,
                          @Value("${app.super-admin.email}") String superAdminEmail) {
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.accountProvisioningService = accountProvisioningService;
        this.superAdminEmail = User.normalizeEmail(superAdminEmail);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminUserDTO> users() {
        currentUserService.require(Role.SUPER_ADMIN);
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AdminUserDTO::from)
                .toList();
    }

    @Override
    @Transactional
    public AdminUserDTO assignRole(AssignRoleRequestDTO request) {
        currentUserService.require(Role.SUPER_ADMIN);
        Role role = requireAssignable(request.getRole());
        String email = User.normalizeEmail(request.getEmail());
        if (email.equals(superAdminEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Супер-админ задаётся переменной окружения SUPER_ADMIN_EMAIL, его роль здесь не меняется");
        }
        User user = userRepository.findByUsername(email)
                .orElseGet(() -> User.builder().username(email).build());
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Роль супер-админа не меняется");
        }
        String name = trimToNull(request.getName());
        if (name != null) {
            user.setName(name);
        } else if (user.getName() == null) {
            user.setName(email.substring(0, email.indexOf('@')));
        }
        user.setRole(role);
        User saved = userRepository.save(user);
        accountProvisioningService.provision(saved);
        return AdminUserDTO.from(saved);
    }

    private Role requireAssignable(Role role) {
        if (role == null || !Role.assignable().contains(role)) {
            String allowed = Role.assignable().stream().map(Role::name).collect(Collectors.joining(", "));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Назначить можно только роль: " + allowed);
        }
        return role;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
