package ru.trafficmarkering.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.trafficmarkering.model.auth.TelegramAuth;

import java.time.Instant;
import java.util.Optional;

public interface TelegramAuthRepository extends JpaRepository<TelegramAuth, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TelegramAuth a where a.secretHash = :hash")
    Optional<TelegramAuth> lockBySecretHash(String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TelegramAuth a where a.id = :id")
    Optional<TelegramAuth> lockById(String id);

    @Modifying
    @Query("delete from TelegramAuth a where a.expiresAt < :before")
    int deleteExpiredBefore(Instant before);
}
