package ru.trafficmarkering.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.profile.CustomerProfile;
import ru.trafficmarkering.repository.GetterCustomerProfile;
import ru.trafficmarkering.repository.SaverCustomerProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
class CustomerProfileManager implements GetterCustomerProfile, SaverCustomerProfile {

    private final CustomerProfileRepo customerProfileRepo;

    @Override
    public Optional<CustomerProfile> getByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        try {
            return customerProfileRepo.findByUserId(userId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public List<CustomerProfile> getAllByUserIds(Collection<Long> userIds) {
        // Пустой IN в SQL — синтаксическая ошибка, поэтому обрываем запрос заранее
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        try {
            return customerProfileRepo.findAllByUserIdIn(userIds);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public CustomerProfile save(CustomerProfile customerProfile) {
        try {
            // saveAndFlush, а не save: сервис тут же собирает DTO из возвращённой сущности,
            // а @CreationTimestamp/@UpdateTimestamp Hibernate проставляет только на flush —
            // без него createdAt/updatedAt уезжают в ответ пустыми или устаревшими
            return customerProfileRepo.saveAndFlush(customerProfile);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
