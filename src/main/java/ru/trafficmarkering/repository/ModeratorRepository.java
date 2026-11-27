package ru.trafficmarkering.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.trafficmarkering.model.moderation.Moderator;

import java.util.List;
import java.util.Optional;

public interface ModeratorRepository extends JpaRepository<Moderator, Long> {

    Optional<Moderator> findByUsername(String username);

    Optional<Moderator> findByTelegramId(Long telegramId);

    List<Moderator> findByEnabledTrueOrderByUsername();

    List<Moderator> findAllByOrderByEnabledDescUsernameAsc();
}
