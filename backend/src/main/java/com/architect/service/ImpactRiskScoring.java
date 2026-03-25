package com.architect.service;

/**
 * Pure risk score math for impact analysis and tests.
 */
public final class ImpactRiskScoring {

    private ImpactRiskScoring() {}

    /**
     * Numeric score 0–10: min(repos×2, 6) + min(files×0.3, 2.5) + min(eps×0.5, 1.5).
     */
    public static double rawScore(int repoCount, int fileCount, int endpointCount) {
        return Math.round((
                Math.min(repoCount * 2.0, 6.0)
                        + Math.min(fileCount * 0.3, 2.5)
                        + Math.min(endpointCount * 0.5, 1.5)
        ) * 10.0) / 10.0;
    }

    public static String label(double score) {
        return score >= 6.0 ? "HIGH" : score >= 3.0 ? "MEDIUM" : "LOW";
    }

    public static String verdict(double score) {
        return score >= 7.0 ? "BLOCKED"
                : score >= 4.0 ? "REVIEW REQUIRED"
                : "SAFE TO MERGE";
    }
}
