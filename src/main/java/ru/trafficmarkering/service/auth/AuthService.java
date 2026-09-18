package ru.trafficmarkering.service.auth;

import ru.trafficmarkering.dto.CurrentUserDTO;
import ru.trafficmarkering.dto.auth.AuthResponseDTO;
import ru.trafficmarkering.dto.auth.RegisterRequestDTO;

public interface AuthService {

    void register(RegisterRequestDTO request);

    void requestCode(String email);

    AuthResponseDTO verify(String email, String code);

    CurrentUserDTO me();
}
