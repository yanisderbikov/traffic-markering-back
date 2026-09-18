package ru.trafficmarkering.repository.impl;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.wallet.Wallet;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
interface WalletRepo extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.user.id = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") Long userId);

    @Query("select w from Wallet w join fetch w.user u where u.role in :roles order by u.createdAt desc")
    List<Wallet> findAllWithUserByRoleIn(@Param("roles") Collection<Role> roles);
}
