package ru.trafficmarkering.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.wallet.Transfer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
interface TransferRepo extends JpaRepository<Transfer, Long> {

    @Query("select t from Transfer t left join fetch t.processedBy where t.transaction.id = :transactionId")
    Optional<Transfer> findByTransactionId(@Param("transactionId") Long transactionId);

    @Query("select t from Transfer t where t.transaction.id in :transactionIds")
    List<Transfer> findAllByTransactionIdIn(@Param("transactionIds") Collection<Long> transactionIds);
}
