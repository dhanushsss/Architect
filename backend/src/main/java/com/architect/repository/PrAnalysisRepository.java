package com.architect.repository;

import com.architect.model.PrAnalysis;
import com.architect.model.Repo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PrAnalysisRepository extends JpaRepository<PrAnalysis, Long> {
    List<PrAnalysis> findByRepoOrderByCreatedAtDesc(Repo repo);
    Optional<PrAnalysis> findByRepoAndPrNumber(Repo repo, Integer prNumber);
}
