package com.architect.controller;

import com.architect.dto.ImpactDto;
import com.architect.model.Repo;
import com.architect.model.User;
import com.architect.repository.RepoRepository;
import com.architect.service.ImpactAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/impact")
@RequiredArgsConstructor
public class AnalysisController {

    private final ImpactAnalysisService impactAnalysisService;
    private final RepoRepository repoRepository;

    @GetMapping("/endpoint/{endpointId}")
    public ResponseEntity<ImpactDto> getEndpointImpact(@AuthenticationPrincipal User user,
                                                       @PathVariable Long endpointId) {
        return ResponseEntity.ok(impactAnalysisService.analyzeEndpoint(endpointId));
    }

    @GetMapping("/repo/{repoId}")
    public ResponseEntity<ImpactDto> getRepoImpact(@AuthenticationPrincipal User user,
                                                   @PathVariable Long repoId) {
        return ResponseEntity.ok(impactAnalysisService.analyzeRepo(repoId));
    }

    /**
     * Change 6 — risk overview for all scanned repos.
     * Returns a lightweight risk card per repo without full impact detail,
     * so the dashboard can show risk-first without N separate API calls.
     */
    @GetMapping("/overview")
    public ResponseEntity<List<Map<String, Object>>> getRiskOverview(@AuthenticationPrincipal User user) {
        List<Repo> repos = repoRepository.findByUserId(user.getId());
        List<Map<String, Object>> result = new ArrayList<>();

        for (Repo repo : repos) {
            if (repo.getScanStatus() != Repo.ScanStatus.COMPLETE) continue;
            try {
                ImpactDto impact = impactAnalysisService.analyzeRepo(repo.getId());
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("repoId", repo.getId());
                card.put("repoName", repo.getName());
                card.put("verdict", impact.getVerdict());
                card.put("numericScore", impact.getNumericScore());
                card.put("riskLabel", impact.getRiskScore());
                card.put("dependentsCount", impact.getDependentsCount());
                card.put("affectedRepos", impact.getAffectedRepos().stream()
                    .map(r -> r.getName()).toList());
                result.add(card);
            } catch (Exception e) {
                // skip repos that fail analysis
            }
        }

        // Sort: highest risk first
        result.sort((a, b) -> Double.compare(
            (double) b.get("numericScore"),
            (double) a.get("numericScore")
        ));

        return ResponseEntity.ok(result);
    }
}
