package com.architect.repository;

import com.architect.model.ScanQueueTask;
import com.architect.model.ScanTaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScanQueueTaskRepository extends JpaRepository<ScanQueueTask, Long> {

    long countByStatus(ScanTaskStatus status);

    long countByUser_IdAndStatus(Long userId, ScanTaskStatus status);

    @EntityGraph(attributePaths = {"repo", "user"})
    @Query("SELECT t FROM ScanQueueTask t WHERE t.status = :st ORDER BY CASE WHEN t.type = com.architect.scan.ScanType.PR THEN 0 ELSE 1 END, t.createdAt ASC")
    List<ScanQueueTask> findQueuedByPriority(@Param("st") ScanTaskStatus st, Pageable pageable);

    @Modifying
    @Query(value = "UPDATE scan_tasks SET status = 'QUEUED', picked_by = NULL, picked_at = NULL WHERE id = :id AND status = 'RUNNING'", nativeQuery = true)
    int revertToQueued(@Param("id") Long id);

    @Query("SELECT t FROM ScanQueueTask t JOIN FETCH t.repo JOIN FETCH t.user WHERE t.id = :id")
    Optional<ScanQueueTask> findByIdForExecution(@Param("id") Long id);
}
