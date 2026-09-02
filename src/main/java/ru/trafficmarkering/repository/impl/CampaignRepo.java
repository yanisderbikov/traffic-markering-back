package ru.trafficmarkering.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.CampaignStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Везде join fetch заказчика: его имя попадает в каждый DTO объявления,
 * а связь ленивая — без fetch доска превращается в N+1 запросов.
 */
@Repository
interface CampaignRepo extends JpaRepository<Campaign, UUID> {

    @Query("select c from Campaign c join fetch c.customer where c.id = :id")
    Optional<Campaign> findByIdWithCustomer(@Param("id") UUID id);

    @Query("select c from Campaign c join fetch c.customer where c.publicId = :publicId")
    Optional<Campaign> findByPublicIdWithCustomer(@Param("publicId") String publicId);

    @Query("select c from Campaign c join fetch c.customer u where u.id = :customerId order by c.createdAt desc")
    List<Campaign> findByCustomerId(@Param("customerId") Long customerId);

    @Query("select c from Campaign c join fetch c.customer where c.status = :status order by c.createdAt desc")
    List<Campaign> findByStatus(@Param("status") CampaignStatus status);

    boolean existsByPublicId(String publicId);
}
