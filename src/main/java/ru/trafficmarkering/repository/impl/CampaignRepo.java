package ru.trafficmarkering.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.CampaignStatus;
import ru.trafficmarkering.repository.CampaignTotals;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Везде join fetch заказчика: его имя попадает в каждый DTO объявления,
 * а связь ленивая — без fetch доска превращается в N+1 запросов.
 * Тематику тянем там, где собирается CampaignDTO, — доске она не нужна.
 */
@Repository
interface CampaignRepo extends JpaRepository<Campaign, UUID> {

    @Query("select c from Campaign c join fetch c.customer left join fetch c.topic where c.id = :id")
    Optional<Campaign> findByIdWithCustomer(@Param("id") UUID id);

    @Query("select c from Campaign c join fetch c.customer left join fetch c.topic where c.publicId = :publicId")
    Optional<Campaign> findByPublicIdWithCustomer(@Param("publicId") String publicId);

    @Query("select c from Campaign c join fetch c.customer u left join fetch c.topic "
            + "where u.id = :customerId order by c.createdAt desc")
    List<Campaign> findByCustomerId(@Param("customerId") Long customerId);

    @Query("select c from Campaign c join fetch c.customer where c.status = :status "
            + "and (c.startsAt is null or c.startsAt <= :now) and (c.endsAt is null or c.endsAt >= :now) "
            + "order by c.createdAt desc")
    List<Campaign> findByStatusWithinPeriod(@Param("status") CampaignStatus status, @Param("now") Instant now);

    boolean existsByPublicId(String publicId);

    @Query("select new ru.trafficmarkering.repository.CampaignTotals(c.customer.id, sum(c.budgetKopecks), sum(c.spentKopecks)) "
            + "from Campaign c group by c.customer.id")
    List<CampaignTotals> totalsByCustomer();

    @Query(value = "select percentile_cont(0.5) within group (order by rate_per_thousand_kopecks) from campaign "
            + "where status <> 'DRAFT' and rate_per_thousand_kopecks > 0", nativeQuery = true)
    Double medianRatePerThousandKopecks();

    @Query(value = "select percentile_cont(0.5) within group (order by budget_kopecks) from campaign "
            + "where status <> 'DRAFT' and budget_kopecks > 0", nativeQuery = true)
    Double medianBudgetKopecks();

    /**
     * Сегмент — тематика. Алиасы в кавычках: без них Postgres приводит их к нижнему регистру,
     * и проекция не находит medianRate. Себя исключаем по public_id — он уникален и строковый.
     */
    @Query(value = "select count(*) as campaigns, "
            + "percentile_cont(0.5) within group (order by rate_per_thousand_kopecks) as \"medianRate\", "
            + "percentile_cont(0.5) within group (order by budget_kopecks) as \"medianBudget\", "
            + "count(*) filter (where rate_per_thousand_kopecks < :rate) as \"lowerRate\", "
            + "count(*) filter (where budget_kopecks < :budget) as \"lowerBudget\" "
            + "from campaign where topic = :topic and public_id <> :publicId and status <> 'DRAFT' "
            + "and rate_per_thousand_kopecks > 0 and budget_kopecks > 0", nativeQuery = true)
    CampaignSegmentRow segmentStats(@Param("topic") String topic,
                                    @Param("publicId") String excludedPublicId,
                                    @Param("rate") long rate,
                                    @Param("budget") long budget);
}
