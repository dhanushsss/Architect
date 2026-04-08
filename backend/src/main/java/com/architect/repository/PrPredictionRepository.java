package com.architect.repository;

import com.architect.model.PrPrediction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrPredictionRepository extends JpaRepository<PrPrediction, Long> {

    Optional<PrPrediction> findTopByRepoFullNameAndPrNumberOrderByCreatedAtDesc(
            String repoFullName, int prNumber);

    List<PrPrediction> findByRepoFullNameOrderByCreatedAtDesc(String repoFullName);
}
