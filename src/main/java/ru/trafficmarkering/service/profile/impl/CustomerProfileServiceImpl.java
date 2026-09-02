package ru.trafficmarkering.service.profile.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.dto.profile.CustomerProfileDTO;
import ru.trafficmarkering.dto.profile.CustomerProfileRequestDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.profile.CustomerProfile;
import ru.trafficmarkering.repository.GetterCustomerProfile;
import ru.trafficmarkering.repository.SaverCustomerProfile;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.profile.CustomerProfileService;

@Service
@RequiredArgsConstructor
class CustomerProfileServiceImpl implements CustomerProfileService {

    private final GetterCustomerProfile getterCustomerProfile;
    private final SaverCustomerProfile saverCustomerProfile;
    private final CurrentUserService currentUserService;

    @Override
    @Transactional
    public CustomerProfileDTO get() {
        User user = currentUserService.require(Role.CUSTOMER);
        return CustomerProfileDTO.from(getOrCreate(user), user);
    }

    @Override
    @Transactional
    public CustomerProfileDTO update(CustomerProfileRequestDTO request) {
        User user = currentUserService.require(Role.CUSTOMER);
        CustomerProfile profile = getOrCreate(user);
        profile.setCompany(trimToNull(request.getCompany()));
        profile.setAbout(trimToNull(request.getAbout()));
        profile.setTelegram(trimToNull(request.getTelegram()));
        profile.setWebsite(trimToNull(request.getWebsite()));
        return CustomerProfileDTO.from(saverCustomerProfile.save(profile), user);
    }

    /**
     * Профиль заводится при регистрации, но у демо-данных и старых учёток его может не быть —
     * создаём на лету, чтобы форма профиля не встречала человека ошибкой.
     */
    private CustomerProfile getOrCreate(User user) {
        return getterCustomerProfile.getByUserId(user.getId())
                .orElseGet(() -> saverCustomerProfile.save(CustomerProfile.builder().user(user).build()));
    }

    /** Очищенное поле формы приходит пустой строкой; в базе это «не заполнено», то есть null. */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
