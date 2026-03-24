package com.architect.repository;

import com.architect.model.AiQueryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AiQueryHistoryRepository extends JpaRepository<AiQueryHistory, Long> {
    List<AiQueryHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
