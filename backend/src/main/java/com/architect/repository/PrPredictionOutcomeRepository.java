package com.architect.repository;

import com.architect.model.PrPredictionOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PrPredictionOutcomeRepository extends JpaRepository<PrPredictionOutcome, Long> {

    Optional<PrPredictionOutcome> findByRepoFullNameAndPrNumber(String repoFullName, int prNumber);

    List<PrPredictionOutcome> findByRepoFullNameAndOutcome(String repoFullName, String outcome);

    List<PrPredictionOutcome> findByOutcome(String outcome);

    @Query("""
        SELECT o FROM PrPredictionOutcome o
        WHERE o.outcome = 'PENDING'
        AND o.mergedAt IS NOT NULL
        ORDER BY o.mergedAt ASC
        """)
    List<PrPredictionOutcome> findPendingOutcomes();

    @Query(value = """
        SELECT repo_full_name,
               COUNT(*) AS total,
               COUNT(*) FILTER (WHERE outcome != 'PENDING') AS resolved,
               COUNT(*) FILTER (WHERE was_prediction_correct = TRUE) AS correct,
               COUNT(*) FILTER (WHERE was_prediction_correct = FALSE) AS incorrect,
               COUNT(*) FILTER (WHERE outcome = 'REVERTED') AS reverted,
               COUNT(*) FILTER (WHERE outcome = 'HOTFIXED') AS hotfixed
        FROM pr_prediction_outcomes
        WHERE repo_full_name = :repoFullName
        GROUP BY repo_full_name
        """, nativeQuery = true)
    Optional<Object[]> getAccuracyStats(String repoFullName);

    @Query(value = """
        SELECT
               COUNT(*) AS total,
               COUNT(*) FILTER (WHERE outcome != 'PENDING') AS resolved,
               COUNT(*) FILTER (WHERE was_prediction_correct = TRUE) AS correct,
               COUNT(*) FILTER (WHERE was_prediction_correct = FALSE) AS incorrect,
               COUNT(*) FILTER (WHERE outcome = 'REVERTED') AS reverted,
               COUNT(*) FILTER (WHERE outcome = 'HOTFIXED') AS hotfixed
        FROM pr_prediction_outcomes
        """, nativeQuery = true)
    Object[] getGlobalAccuracyStats();
}
