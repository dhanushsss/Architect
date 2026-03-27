package com.architect.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImpactDto {
    private String subjectId;
    private String subjectType;
    private String subjectLabel;

    /** Legacy label: HIGH / MEDIUM / LOW — kept for backwards compat */
    private String riskScore;

    /** 0.0 – 10.0 numeric risk score */
    private double numericScore;

    /** SAFE TO MERGE / REVIEW REQUIRED / BLOCKED */
    private String verdict;

    private int dependentsCount;
    private List<AffectedItem> affectedRepos;
    private List<AffectedItem> affectedFiles;
    private List<String> orphanEndpoints;

    /** Changed API endpoints that triggered this analysis (PR context) */
    private List<String> changedEndpoints;

    /** Touched in PR with no cross-repo callers — Scenario 4 (orphan / hidden risk) */
    private List<String> prOrphanEndpoints;

    /** 0–100 how much to trust this analysis (graph freshness, match quality) */
    private Double confidenceScore;

    /** UNRESOLVED outbound calls from affected repos (PR confidence deduction). */
    private Integer unresolvedCallCount;

    /** Exact endpoint matches (method + normalized path) found during PR analysis. */
    private Integer directMatchCount;

    /** Non-exact but plausible matches (wildcard/suffix/method fallback) found during PR analysis. */
    private Integer inferredMatchCount;

    /** Repos in the analyzed scope with last scan older than 48h. */
    private Integer staleRepoCount;

    /** Repos in the analyzed scope that have never been scanned. */
    private Integer unscannedRepoCount;

    /** Changed files in PR that were not fetched at PR head for extraction. */
    private Integer changedFilesNotFetched;

    /** Phase 2 — human-readable “why risky” bullets for PR comment + UI */
    private List<String> riskFactors;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AffectedItem {
        private String id;
        private String name;
        private String type;
        private String detail;
    }
}
