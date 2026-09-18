package ru.trafficmarkering.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;

import java.util.List;
import java.util.UUID;

@Repository
interface ApplicationRepo extends JpaRepository<Application, UUID> {

    /**
     * join fetch — потому что в любом списке откликов сразу нужны заголовок объявления
     * и имя криатора, а обе связи ленивые: без него получаем запрос на каждую строку.
     */
    @Query("select a from Application a join fetch a.campaign join fetch a.creator "
            + "where a.campaign.id = :campaignId order by a.createdAt asc")
    List<Application> findByCampaignIdOrderByCreatedAtAsc(@Param("campaignId") UUID campaignId);

    @Query("select a from Application a join fetch a.campaign join fetch a.creator "
            + "where a.creator.id = :creatorId order by a.createdAt desc")
    List<Application> findByCreatorIdOrderByCreatedAtDesc(@Param("creatorId") Long creatorId);

    @Query("select a from Application a join fetch a.campaign join fetch a.creator "
            + "where a.status = :status order by a.createdAt asc")
    List<Application> findByStatus(@Param("status") ApplicationStatus status);

    long countByCampaignId(UUID campaignId);

    @Query("select count(a) from Application a "
            + "where a.campaign.id = :campaignId and a.creator.id = :creatorId and a.status <> :excluded")
    long countByCampaignIdAndCreatorId(@Param("campaignId") UUID campaignId,
                                       @Param("creatorId") Long creatorId,
                                       @Param("excluded") ApplicationStatus excluded);

    @Query("select a from Application a join fetch a.campaign join fetch a.creator "
            + "where a.videoKey = :videoKey and a.status <> :excluded")
    List<Application> findByVideoKey(@Param("videoKey") String videoKey,
                                     @Param("excluded") ApplicationStatus excluded);

    boolean existsByPublicId(String publicId);

    @Query("select a from Application a join fetch a.campaign join fetch a.creator "
            + "where a.accruedKopecks > a.creditedKopecks order by a.creator.id asc, a.createdAt asc")
    List<Application> findCreditable();
}
