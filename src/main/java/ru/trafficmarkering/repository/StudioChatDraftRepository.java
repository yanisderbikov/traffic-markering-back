package ru.trafficmarkering.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.trafficmarkering.model.studio.StudioChatDraft;

import java.time.Instant;
import java.util.UUID;

public interface StudioChatDraftRepository extends JpaRepository<StudioChatDraft, UUID> {

    @Modifying
    @Query("DELETE FROM StudioChatDraft d WHERE d.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
