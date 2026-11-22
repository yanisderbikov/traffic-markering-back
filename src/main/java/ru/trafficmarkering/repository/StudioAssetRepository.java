package ru.trafficmarkering.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.trafficmarkering.model.studio.StudioAsset;

import java.util.List;
import java.util.UUID;

public interface StudioAssetRepository extends JpaRepository<StudioAsset, UUID> {

    /** Свои файлы и общий каталог */
    @Query("SELECT a FROM StudioAsset a WHERE a.userId = :userId OR a.shared = true ORDER BY a.shared, a.createdAt DESC")
    List<StudioAsset> findVisible(@Param("userId") Long userId);

    long countByUserIdAndSharedFalse(Long userId);
}
