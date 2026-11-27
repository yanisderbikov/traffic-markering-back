package ru.trafficmarkering.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.application.PlacementState;

import ru.trafficmarkering.model.fraud.FraudStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
interface ApplicationRepo extends JpaRepository<Application, UUID> {

    /**
     * join fetch — потому что в любом списке откликов сразу нужны заголовок объявления
     * и имя креатора, а обе связи ленивые: без него получаем запрос на каждую строку.
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

    @Query("select a from Application a join fetch a.campaign join fetch a.creator "
            + "where a.campaign.id = :campaignId and a.creator.id = :creatorId and a.status = :status "
            + "order by a.createdAt asc")
    List<Application> findByCampaignIdAndCreatorIdAndStatus(@Param("campaignId") UUID campaignId,
                                                             @Param("creatorId") Long creatorId,
                                                             @Param("status") ApplicationStatus status);

    boolean existsByPublicId(String publicId);

    /** Очередь модерации: ролики на проверке и поданные работы, у которых надбавка ждёт решения. */
    @Query("select a from Application a join fetch a.campaign join fetch a.creator "
            + "where a.status = :pending or (a.status in :working "
            + "and (a.linkPlacement.state = :placementPending or a.promoPlacement.state = :placementPending)) "
            + "order by a.createdAt asc")
    List<Application> findAwaitingModeration(@Param("pending") ApplicationStatus pending,
                                             @Param("working") Collection<ApplicationStatus> working,
                                             @Param("placementPending") PlacementState placementPending);

    /** Работы с подтверждёнными, но ещё условными надбавками, у которых истёк срок размещения. */
    @Query("select a from Application a join fetch a.campaign join fetch a.creator "
            + "where a.placementRetainUntil <= :now and a.status in :working "
            + "and (a.linkPlacement.state = :verified or a.promoPlacement.state = :verified) "
            + "order by a.placementRetainUntil asc")
    List<Application> findPlacementsDue(@Param("now") Instant now,
                                        @Param("working") Collection<ApplicationStatus> working,
                                        @Param("verified") PlacementState verified);

    @Query("select a from Application a join fetch a.campaign join fetch a.creator "
            + "where a.accruedKopecks > a.creditedKopecks order by a.creator.id asc, a.createdAt asc")
    List<Application> findCreditable();

    @Query("select a from Application a join fetch a.campaign join fetch a.creator "
            + "where a.status in :statuses order by a.createdAt asc")
    List<Application> findByStatusIn(@Param("statuses") Collection<ApplicationStatus> statuses);

    @Query("select a from Application a join fetch a.campaign join fetch a.creator "
            + "where a.fraudStatus in :statuses order by a.fraudScore desc, a.updatedAt desc")
    List<Application> findByFraudStatusIn(@Param("statuses") Collection<FraudStatus> statuses);
}
