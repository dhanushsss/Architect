package com.architect.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Serializable payload for PR risk LLM explanation (no graph mutation).
 */
public record AiRiskInput(
        List<String> changedEndpoints,
        List<String> affectedRepos,
        List<String> affectedFiles,
        List<String> callSites,
        String riskLevel,
        int confidence,
        int unresolvedCalls,
        int staleRepos,
        int unscannedRepos
) {
    public static AiRiskInput fromImpact(ImpactDto impact) {
        List<String> endpoints = impact.getChangedEndpoints() != null
                ? List.copyOf(impact.getChangedEndpoints())
                : List.of();
        List<String> repos = new ArrayList<>();
        if (impact.getAffectedRepos() != null) {
            for (ImpactDto.AffectedItem item : impact.getAffectedRepos()) {
                if (item.getName() != null && !item.getName().isBlank()) {
                    repos.add(item.getName());
                }
            }
        }
        List<String> files = new ArrayList<>();
        List<String> callSites = new ArrayList<>();
        if (impact.getAffectedFiles() != null) {
            for (ImpactDto.AffectedItem f : impact.getAffectedFiles()) {
                String name = Objects.toString(f.getName(), "");
                String detail = Objects.toString(f.getDetail(), "");
                if (!name.isBlank() || !detail.isBlank()) {
                    files.add((detail + " " + name).trim());
                }
                if (!detail.isBlank() || !name.isBlank()) {
                    callSites.add((detail + " :: " + name).trim());
                }
            }
        }
        int conf = impact.getConfidenceScore() != null
                ? impact.getConfidenceScore().intValue()
                : 0;
        int unresolved = impact.getUnresolvedCallCount() != null ? impact.getUnresolvedCallCount() : 0;
        int stale = impact.getStaleRepoCount() != null ? impact.getStaleRepoCount() : 0;
        int unscanned = impact.getUnscannedRepoCount() != null ? impact.getUnscannedRepoCount() : 0;
        return new AiRiskInput(
                endpoints,
                List.copyOf(repos),
                List.copyOf(files),
                List.copyOf(callSites),
                riskLevelFromVerdict(impact.getVerdict()),
                conf,
                unresolved,
                stale,
                unscanned
        );
    }

    private static String riskLevelFromVerdict(String verdict) {
        if (verdict == null) {
            return "MEDIUM";
        }
        if ("SAFE TO MERGE".equalsIgnoreCase(verdict)) {
            return "LOW";
        }
        if ("BLOCKED".equalsIgnoreCase(verdict)) {
            return "HIGH";
        }
        return "MEDIUM";
    }
}
