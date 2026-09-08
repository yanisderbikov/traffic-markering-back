package ru.trafficmarkering.service.social;

import ru.trafficmarkering.dto.social.SocialAccountDTO;
import ru.trafficmarkering.dto.social.SocialAuthorizeResponseDTO;

import java.util.List;
import java.util.UUID;

public interface SocialAccountService {

    List<SocialAccountDTO> listMine();

    SocialAuthorizeResponseDTO authorize(String platformSlug);

    SocialAccountDTO connect(String platformSlug, String code, String state);

    void disconnect(UUID id);

    String frontRedirect(String platformSlug, String status, String message);
}
