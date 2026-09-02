package ru.trafficmarkering.service.auth;

import ru.trafficmarkering.model.Role;

public interface JwtTokenService {
    String createToken(String username, Role role, String name);

    boolean isValid(String token);

    boolean isServiceToken(String token);

    String getUsername(String token);

    Role getRole(String token);
}
