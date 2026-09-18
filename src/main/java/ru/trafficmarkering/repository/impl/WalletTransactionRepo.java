package ru.trafficmarkering.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.model.wallet.WalletTransactionType;

import java.util.List;
import java.util.Optional;

@Repository
interface WalletTransactionRepo extends JpaRepository<WalletTransaction, Long> {

    @Query("select t from WalletTransaction t join fetch t.wallet w join fetch w.user "
            + "left join fetch t.campaign left join fetch t.actor "
            + "where w.id = :walletId order by t.createdAt desc, t.id desc")
    List<WalletTransaction> findByWalletId(@Param("walletId") Long walletId);

    @Query("select t from WalletTransaction t join fetch t.wallet w join fetch w.user "
            + "left join fetch t.campaign left join fetch t.actor order by t.createdAt desc, t.id desc")
    List<WalletTransaction> findAllWithDetails();

    @Query("select t from WalletTransaction t join fetch t.wallet w join fetch w.user "
            + "left join fetch t.campaign left join fetch t.actor where t.id = :id")
    Optional<WalletTransaction> findByIdWithDetails(@Param("id") Long id);

    @Query("select t from WalletTransaction t join fetch t.wallet w join fetch w.user "
            + "left join fetch t.campaign left join fetch t.actor "
            + "where t.type = :type order by t.createdAt desc, t.id desc")
    List<WalletTransaction> findByType(@Param("type") WalletTransactionType type);
}
