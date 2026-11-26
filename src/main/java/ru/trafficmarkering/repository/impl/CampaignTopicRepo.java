package ru.trafficmarkering.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.campaign.CampaignTopic;

import java.util.List;
import java.util.Optional;

@Repository
interface CampaignTopicRepo extends JpaRepository<CampaignTopic, String> {

    Optional<CampaignTopic> findByNormalizedName(String normalizedName);

    /**
     * Популярность — число запущенных объявлений: черновики не считаем, иначе тематика
     * попадала бы в топ просто оттого, что её выбрали и бросили. При равенстве базовые
     * (заведены миграцией, created_at самый ранний) идут раньше добавленных заказчиками.
     * В LIKE экранируем «!»: обратный слеш Hibernate в нативных запросах путает с кавычкой.
     */
    @Query(value = "select t.* from campaign_topic t "
            + "left join campaign c on c.topic = t.code and c.status <> 'DRAFT' "
            + "where t.normalized_name like :contains escape '!' "
            + "group by t.code "
            + "order by t.normalized_name like :prefix escape '!' desc, count(c.id) desc, t.created_at, t.name "
            + "limit :limit", nativeQuery = true)
    List<CampaignTopic> search(@Param("contains") String contains,
                               @Param("prefix") String prefix,
                               @Param("limit") int limit);
}
