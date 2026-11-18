package ru.trafficmarkering.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.LoginCode;

import java.util.Optional;

@Repository
interface LoginCodeRepo extends JpaRepository<LoginCode, Long> {

    Optional<LoginCode> findTopByEmailAndUsedFalseOrderByCreatedAtDesc(String email);

    @Modifying
    @Query("UPDATE LoginCode c SET c.used = true WHERE c.email = :email AND c.used = false")
    void invalidateAllByEmail(String email);
}
