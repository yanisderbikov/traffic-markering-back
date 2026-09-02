package ru.trafficmarkering.service.profile;

import ru.trafficmarkering.dto.profile.CustomerProfileDTO;
import ru.trafficmarkering.dto.profile.CustomerProfileRequestDTO;

public interface CustomerProfileService {

    /** Профиль текущего заказчика. Если строки почему-то нет — заводит пустую, а не отдаёт 404. */
    CustomerProfileDTO get();

    CustomerProfileDTO update(CustomerProfileRequestDTO request);
}
