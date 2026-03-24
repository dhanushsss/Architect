package com.architect.service;

import com.architect.dto.ImpactDto;
import com.architect.model.Repo;
import com.architect.repository.ApiEndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 2: confidence scoring + “why risky” explanations for PR comments and dashboard.
 */
@Service
@RequiredArgsConstructor
public class PrRiskEnrichmentService {

    private final ApiEndpointRepository apiEndpointRepository;

    public void enrich(ImpactDto impact, Repo repo, PrCommentFormatter.Scenario scenario,
                       int changedFileCount, int prHeadFilesFetched) {
        List<String> factors = new ArrayList<>();

        int deps = impact.getDependentsCount();
        int callSites = impact.getAffectedFiles() != null ? impact.getAffectedFiles().size() : 0;
        List<String> changed = impact.getChangedEndpoints();
        int epTouch = changed != null ? changed.size() : 0;

        if (deps > 0) {
            factors.add(String.format(
                "**Cross-service usage:** Architect’s graph shows **%d** other connected repo(s) with **%d** tracked call site(s) into APIs touched by this PR.",
                deps, Math.max(callSites, 1)));
        }

        if (scenario == PrCommentFormatter.Scenario.ORPHAN_API_RISK) {
            factors.add("**No tracked callers:** Either this API is unused, or clients use dynamic URLs / mobile / external apps Architect does not index.");
        }

        if (scenario == PrCommentFormatter.Scenario.WIDE_CASCADING || scenario == PrCommentFormatter.Scenario.CRITICAL_CROSS_SERVICE) {
            factors.add("**Blast radius:** Many downstream consumers share the same contract — a breaking change propagates widely.");
        }

        if (epTouch > 0 && deps == 0 && scenario != PrCommentFormatter.Scenario.ORPHAN_API_RISK) {
            factors.add("**API surface changed** in " + epTouch + " route(s); no cross-repo callers were found in the last graph scan.");
        }

        long epCount = apiEndpointRepository.countByRepo(repo);
        factors.add(String.format(
            "**Scope:** Analysis used **%d** changed file(s), **%d** fetched at PR head; dependency graph has **%d** endpoints indexed for `%s`.",
            changedFileCount, prHeadFilesFetched, epCount, repo.getName()));

        LocalDateTime scanned = repo.getLastScannedAt();
        if (scanned == null) {
            factors.add("**Freshness:** This repo has **never been fully scanned** — run a deep scan for best accuracy.");
        } else {
            long days = Duration.between(scanned, LocalDateTime.now()).toDays();
            if (days > 14) {
                factors.add(String.format("**Stale graph:** Last full scan was **%d days ago** — rescan to reduce false positives/negatives.", days));
            } else if (days > 7) {
                factors.add("**Graph age:** Last scan **" + days + " days ago** — consider rescanning before high-risk merges.");
            }
        }

        factors.add("**Internal vs external:** Callers listed are **repos you connected** in Architect; traffic from unconnected repos is invisible.");

        double confidence = computeConfidence(repo, impact, scenario, prHeadFilesFetched, changedFileCount);
        impact.setConfidenceScore(confidence);
        impact.setRiskFactors(factors);
    }

    private double computeConfidence(Repo repo, ImpactDto impact, PrCommentFormatter.Scenario scenario,
                                     int fetched, int changedFiles) {
        double c = 85.0;
        if (repo.getLastScannedAt() == null) c -= 30;
        else {
            long days = Duration.between(repo.getLastScannedAt(), LocalDateTime.now()).toDays();
            if (days > 14) c -= 20;
            else if (days > 7) c -= 10;
        }
        if (changedFiles > 0 && fetched < Math.min(changedFiles, 5)) c -= 8;
        if (scenario == PrCommentFormatter.Scenario.ORPHAN_API_RISK) c -= 12;
        if (impact.getDependentsCount() == 0 && scenario == PrCommentFormatter.Scenario.SAFE_REFACTOR) c += 5;
        return Math.max(15, Math.min(100, Math.round(c)));
    }
}
