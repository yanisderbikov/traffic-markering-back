package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.profile.CreatorProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GetterCreatorProfile {

    /** Профиль криатора по id учётной записи: связь один-к-одному, поэтому не список. */
    Optional<CreatorProfile> getByUserId(Long userId);

    /**
     * Профили сразу для пачки криаторов — чтобы список откликов не превращался
     * в N+1 запросов ради одного телеграма на карточку.
     */
    List<CreatorProfile> getAllByUserIds(Collection<Long> userIds);
}
