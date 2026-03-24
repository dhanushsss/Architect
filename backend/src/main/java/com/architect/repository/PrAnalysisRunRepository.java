package com.architect.repository;

import com.architect.model.PrAnalysisRun;
import com.architect.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PrAnalysisRunRepository extends JpaRepository<PrAnalysisRun, Long> {

    @Query("SELECT p FROM PrAnalysisRun p WHERE p.user = :user AND p.createdAt >= :since ORDER BY p.numericScore DESC NULLS LAST, p.createdAt DESC")
    List<PrAnalysisRun> findRiskySince(@Param("user") User user, @Param("since") LocalDateTime since);

    @Query("SELECT p FROM PrAnalysisRun p WHERE p.user = :user AND p.repo.id = :repoId AND p.githubPrNumber = :pr ORDER BY p.createdAt DESC")
    List<PrAnalysisRun> findLatestForPr(@Param("user") User user, @Param("repoId") Long repoId, @Param("pr") int pr);
}
