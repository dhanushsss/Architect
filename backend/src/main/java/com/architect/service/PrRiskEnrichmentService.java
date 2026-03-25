package com.architect.service;

import com.architect.dto.ImpactDto;
import com.architect.model.Repo;
import com.architect.repository.ApiCallRepository;
import com.architect.repository.ApiEndpointRepository;
import com.architect.repository.RepoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 2: confidence scoring + “why risky” explanations for PR comments and dashboard.
 */
@Service
@RequiredArgsConstructor
public class PrRiskEnrichmentService {

    private final ApiEndpointRepository apiEndpointRepository;
    private final ApiCallRepository apiCallRepository;
    private final RepoRepository repoRepository;

    public void enrich(ImpactDto impact, Repo repo, PrCommentFormatter.Scenario scenario,
                       int changedFileCount, int prHeadFilesFetched) {
        List<String> factors = new ArrayList<>();

        int deps = impact.getDependentsCount();
        int callSites = impact.getAffectedFiles() != null ? impact.getAffectedFiles().size() : 0;
        List<String> changed = impact.getChangedEndpoints();
        int epTouch = changed != null ? changed.size() : 0;

        if (deps > 0) {
            factors.add(String.format(
                "**Cross-service usage:** Zerqis’s graph shows **%d** other connected repo(s) with **%d** tracked call site(s) into APIs touched by this PR.",
                deps, Math.max(callSites, 1)));
        }

        if (scenario == PrCommentFormatter.Scenario.ORPHAN_API_RISK) {
            factors.add("**No tracked callers:** Either this API is unused, or clients use dynamic URLs / mobile / external apps Zerqis does not index.");
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

        int coverageGap = Math.max(0, changedFileCount - prHeadFilesFetched);
        if (coverageGap > 0) {
            factors.add("**Coverage gap:** " + coverageGap + " changed file(s) could not be fetched at PR head and were excluded.");
        }

        LocalDateTime scanned = repo.getLastScannedAt();
        if (scanned == null) {
            factors.add("**Freshness:** This repo has **never been fully scanned** — run a deep scan for best accuracy.");
        } else {
            long hours = Duration.between(scanned, LocalDateTime.now()).toHours();
            if (hours > 48) {
                factors.add(String.format("**Stale graph:** Last full scan was **%d hours ago** — rescan to reduce false positives/negatives.", hours));
            } else if (hours > 24) {
                factors.add("**Graph age:** Last scan **" + hours + " hours ago** — consider rescanning before high-risk merges.");
            }
        }

        factors.add("**Internal vs external:** Callers listed are **repos you connected** in Zerqis; traffic from unconnected repos is invisible.");

        if (impact.getConfidenceScore() == null) {
            double confidence = computeConfidence(repo, impact, scenario, prHeadFilesFetched, changedFileCount);
            impact.setConfidenceScore(confidence);
        }
        if (impact.getUnresolvedCallCount() == null) {
            impact.setUnresolvedCallCount(estimateUnresolvedCalls(repo, impact));
        }
        applyUnknownCounters(repo, impact, changedFileCount, prHeadFilesFetched);
        impact.setRiskFactors(factors);
    }

    private double computeConfidence(Repo repo, ImpactDto impact, PrCommentFormatter.Scenario scenario,
                                     int fetched, int changedFiles) {
        double c = 85.0;
        if (repo.getLastScannedAt() == null) c -= 30;
        else {
            long hours = Duration.between(repo.getLastScannedAt(), LocalDateTime.now()).toHours();
            if (hours > 48) c -= 20;
            else if (hours > 24) c -= 10;
        }
        if (changedFiles > 0 && fetched < Math.min(changedFiles, 5)) c -= 8;
        if (scenario == PrCommentFormatter.Scenario.ORPHAN_API_RISK) c -= 12;
        if (impact.getDependentsCount() == 0 && scenario == PrCommentFormatter.Scenario.SAFE_REFACTOR) c += 5;
        return Math.max(15, Math.min(100, Math.round(c)));
    }

    private int estimateUnresolvedCalls(Repo sourceRepo, ImpactDto impact) {
        Set<Long> repoIds = new LinkedHashSet<>();
        repoIds.add(sourceRepo.getId());
        if (impact.getAffectedRepos() != null) {
            for (ImpactDto.AffectedItem item : impact.getAffectedRepos()) {
                try {
                    repoIds.add(Long.parseLong(item.getId()));
                } catch (Exception ignored) {
                    // skip malformed ids
                }
            }
        }
        return (int) apiCallRepository.countByCallerRepoIdInAndTargetKind(
                repoIds, ApiCallUrlNormalizer.KIND_UNRESOLVED);
    }

    private void applyUnknownCounters(Repo sourceRepo, ImpactDto impact, int changedFiles, int fetchedFiles) {
        Set<Long> repoIds = new LinkedHashSet<>();
        repoIds.add(sourceRepo.getId());
        if (impact.getAffectedRepos() != null) {
            for (ImpactDto.AffectedItem item : impact.getAffectedRepos()) {
                try {
                    repoIds.add(Long.parseLong(item.getId()));
                } catch (Exception ignored) {
                    // skip malformed ids
                }
            }
        }

        int stale = 0;
        int unscanned = 0;
        LocalDateTime staleCutoff = LocalDateTime.now().minusHours(48);
        for (Long repoId : repoIds) {
            Repo r = repoRepository.findById(repoId).orElse(null);
            if (r == null || r.getLastScannedAt() == null) {
                unscanned++;
                continue;
            }
            if (r.getLastScannedAt().isBefore(staleCutoff)) {
                stale++;
            }
        }
        impact.setStaleRepoCount(stale);
        impact.setUnscannedRepoCount(unscanned);
        impact.setChangedFilesNotFetched(Math.max(0, changedFiles - fetchedFiles));
    }
}
