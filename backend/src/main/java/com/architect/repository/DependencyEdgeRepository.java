package com.architect.repository;

import com.architect.model.DependencyEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DependencyEdgeRepository extends JpaRepository<DependencyEdge, Long> {
    List<DependencyEdge> findBySourceIdAndSourceType(Long sourceId, String sourceType);
    List<DependencyEdge> findByTargetIdAndTargetType(Long targetId, String targetType);

    @Query("SELECT e FROM DependencyEdge e WHERE (e.sourceId = :id AND e.sourceType = :type) OR (e.targetId = :id AND e.targetType = :type)")
    List<DependencyEdge> findAllConnected(@Param("id") Long id, @Param("type") String type);

    void deleteBySourceIdAndSourceType(Long sourceId, String sourceType);
}
