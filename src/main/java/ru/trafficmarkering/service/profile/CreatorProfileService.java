package ru.trafficmarkering.service.profile;

import ru.trafficmarkering.dto.profile.CreatorProfileDTO;
import ru.trafficmarkering.dto.profile.CreatorProfileRequestDTO;

public interface CreatorProfileService {

    /** Профиль текущего креатора. Если строки почему-то нет — заводит пустую, а не отдаёт 404. */
    CreatorProfileDTO get();

    CreatorProfileDTO update(CreatorProfileRequestDTO request);

    /**
     * Профиль креатора для публичного просмотра: по нему заказчик решает,
     * брать ли отклик, а неавторизованный гость — интересна ли площадка.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404, если такого креатора нет
     */
    CreatorProfileDTO getPublicByUserId(Long userId);
}
