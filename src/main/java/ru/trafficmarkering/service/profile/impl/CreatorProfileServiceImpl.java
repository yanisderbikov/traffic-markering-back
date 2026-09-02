package ru.trafficmarkering.service.profile.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.profile.CreatorProfileDTO;
import ru.trafficmarkering.dto.profile.CreatorProfileRequestDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.profile.CreatorProfile;
import ru.trafficmarkering.repository.GetterCreatorProfile;
import ru.trafficmarkering.repository.SaverCreatorProfile;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.profile.CreatorProfileService;

@Service
@RequiredArgsConstructor
class CreatorProfileServiceImpl implements CreatorProfileService {

    private final GetterCreatorProfile getterCreatorProfile;
    private final SaverCreatorProfile saverCreatorProfile;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CreatorProfileDTO get() {
        User user = currentUserService.require(Role.CREATOR);
        return CreatorProfileDTO.from(getOrCreate(user), user);
    }

    @Override
    @Transactional
    public CreatorProfileDTO update(CreatorProfileRequestDTO request) {
        User user = currentUserService.require(Role.CREATOR);
        CreatorProfile profile = getOrCreate(user);
        profile.setDisplayName(trimToNull(request.getDisplayName()));
        profile.setBio(trimToNull(request.getBio()));
        profile.setTelegram(trimToNull(request.getTelegram()));
        profile.setInstagram(trimToNull(request.getInstagram()));
        profile.setTiktok(trimToNull(request.getTiktok()));
        profile.setYoutubeShorts(trimToNull(request.getYoutubeShorts()));
        return CreatorProfileDTO.from(saverCreatorProfile.save(profile), user);
    }

    @Override
    @Transactional(readOnly = true)
    public CreatorProfileDTO getPublicByUserId(Long userId) {
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (user == null || user.getRole() != Role.CREATOR) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Криатор не найден");
        }
        // Публичная ручка ничего не пишет в базу: если профиля почему-то нет,
        // отдаём пустую карточку — имя криатора всё равно есть в учётной записи
        return getterCreatorProfile.getByUserId(userId)
                .map(profile -> CreatorProfileDTO.from(profile, user))
                .orElseGet(() -> CreatorProfileDTO.from(CreatorProfile.builder().user(user).build(), user));
    }

    /**
     * Профиль заводится при регистрации, но у демо-данных и старых учёток его может не быть —
     * создаём на лету, чтобы форма профиля не встречала человека ошибкой.
     */
    private CreatorProfile getOrCreate(User user) {
        return getterCreatorProfile.getByUserId(user.getId())
                .orElseGet(() -> saverCreatorProfile.save(CreatorProfile.builder().user(user).build()));
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
