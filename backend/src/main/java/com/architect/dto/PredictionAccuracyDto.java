package com.architect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Prediction accuracy stats — how well did Zerqis predict PR risk?
 * Exposed via the accuracy API and fed into the dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionAccuracyDto {

    private String repoFullName;     // null for global stats
    private int totalPredictions;
    private int resolvedPredictions;
    private int correctPredictions;
    private int incorrectPredictions;
    private int revertedCount;
    private int hotfixedCount;
    private Double accuracyPct;      // null if no resolved predictions
}
