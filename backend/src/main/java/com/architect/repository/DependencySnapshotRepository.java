package com.architect.repository;

import com.architect.model.DependencySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DependencySnapshotRepository extends JpaRepository<DependencySnapshot, Long> {
    List<DependencySnapshot> findByUserIdOrderByCreatedAtDesc(Long userId);
}
