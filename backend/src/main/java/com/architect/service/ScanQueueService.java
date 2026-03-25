package com.architect.service;

import com.architect.model.Repo;
import com.architect.model.ScanQueueTask;
import com.architect.model.ScanTaskStatus;
import com.architect.model.User;
import com.architect.repository.RepoRepository;
import com.architect.repository.ScanQueueTaskRepository;
import com.architect.repository.UserRepository;
import com.architect.scan.ScanTask;
import com.architect.scan.ScanType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScanQueueService {

    private static final String CLAIM_SQL = """
            SELECT st.id FROM scan_tasks st
            WHERE st.status = 'QUEUED'
            AND (SELECT COUNT(*) FROM scan_tasks r WHERE r.status = 'RUNNING' AND r.user_id = st.user_id) < 2
            AND (SELECT COUNT(*) FROM scan_tasks r2 WHERE r2.status = 'RUNNING') < 10
            ORDER BY CASE WHEN st.type = 'PR' THEN 0 ELSE 1 END, st.created_at ASC
            FOR UPDATE OF st SKIP LOCKED
            LIMIT 1
            """;

    private final ScanQueueTaskRepository scanQueueTaskRepository;
    private final RepoRepository repoRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Long enqueue(Long repoId, Long userId, ScanType type, RepoScannerService.ScanMode mode) {
        Repo repo = repoRepository.findById(repoId)
                .filter(r -> r.getUser().getId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Repo not found or access denied: " + repoId));
        User user = userRepository.findById(userId).orElseThrow();

        ScanQueueTask row = ScanQueueTask.builder()
                .repo(repo)
                .user(user)
                .type(type)
                .scanMode(mode.name())
                .status(ScanTaskStatus.QUEUED)
                .build();
        scanQueueTaskRepository.save(row);
        long queueSize = scanQueueTaskRepository.countByStatus(ScanTaskStatus.QUEUED);
        log.info("scan_enqueued repo={} user={} type={} queueSize={}", repoId, userId, type, queueSize);
        return row.getId();
    }

    /**
     * Atomically claims the next eligible row (priority: PR before MANUAL, then FIFO) if global/user
     * RUNNING counts allow it. Multi-instance safe via {@code FOR UPDATE SKIP LOCKED}.
     */
    @Transactional
    public Optional<Long> claimNext(String instanceId, Instant pickedAt) {
        @SuppressWarnings("unchecked")
        List<Number> ids = entityManager.createNativeQuery(CLAIM_SQL).getResultList();
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        Long id = ids.get(0).longValue();
        int updated = entityManager.createNativeQuery(
                        "UPDATE scan_tasks SET status = 'RUNNING', picked_by = ?1, picked_at = ?2 WHERE id = ?3 AND status = 'QUEUED'")
                .setParameter(1, instanceId)
                .setParameter(2, pickedAt)
                .setParameter(3, id)
                .executeUpdate();
        if (updated == 0) {
            return Optional.empty();
        }
        entityManager.flush();
        return Optional.of(id);
    }

    /**
     * Peek at the next queued task (PR before MANUAL, then oldest {@code created_at}) without claiming.
     * Ordering matches {@link #claimNext}.
     */
    @Transactional(readOnly = true)
    public Optional<ScanTask> poll() {
        List<ScanQueueTask> top = scanQueueTaskRepository.findQueuedByPriority(
                ScanTaskStatus.QUEUED, PageRequest.of(0, 1));
        return top.isEmpty() ? Optional.empty() : Optional.of(top.getFirst())
                .map(t -> ScanTask.builder()
                        .repoId(t.getRepo().getId())
                        .userId(t.getUser().getId())
                        .type(t.getType())
                        .createdAt(t.getCreatedAt())
                        .build());
    }

    public int size() {
        return (int) scanQueueTaskRepository.countByStatus(ScanTaskStatus.QUEUED);
    }
}
