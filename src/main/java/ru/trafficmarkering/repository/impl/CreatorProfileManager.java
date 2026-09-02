package ru.trafficmarkering.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.profile.CreatorProfile;
import ru.trafficmarkering.repository.GetterCreatorProfile;
import ru.trafficmarkering.repository.SaverCreatorProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
class CreatorProfileManager implements GetterCreatorProfile, SaverCreatorProfile {

    private final CreatorProfileRepo creatorProfileRepo;

    @Override
    public Optional<CreatorProfile> getByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        try {
            return creatorProfileRepo.findByUserId(userId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<CreatorProfile> getAllByUserIds(Collection<Long> userIds) {
        // Пустой IN в SQL — синтаксическая ошибка, поэтому обрываем запрос заранее
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        try {
            return creatorProfileRepo.findAllByUserIdIn(userIds);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public CreatorProfile save(CreatorProfile creatorProfile) {
        try {
            // saveAndFlush, а не save: сервис тут же собирает DTO из возвращённой сущности,
            // а @CreationTimestamp/@UpdateTimestamp Hibernate проставляет только на flush —
            // без него createdAt/updatedAt уезжают в ответ пустыми или устаревшими
            return creatorProfileRepo.saveAndFlush(creatorProfile);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
