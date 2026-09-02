package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.profile.CustomerProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GetterCustomerProfile {

    /** Профиль заказчика по id учётной записи: связь один-к-одному, поэтому не список. */
    Optional<CustomerProfile> getByUserId(Long userId);

    /**
     * Профили сразу для пачки заказчиков — на доске объявлений название компании
     * нужно для каждой карточки, а объявлений там десятки.
     */
    List<CustomerProfile> getAllByUserIds(Collection<Long> userIds);
}
