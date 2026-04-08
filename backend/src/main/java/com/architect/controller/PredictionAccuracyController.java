package com.architect.controller;

import com.architect.dto.PredictionAccuracyDto;
import com.architect.repository.PrPredictionOutcomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * API for prediction accuracy — how well is Zerqis predicting PR risk?
 * Used by dashboards and for closed-loop trust calibration.
 */
@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
public class PredictionAccuracyController {

    private final PrPredictionOutcomeRepository outcomeRepository;

    /** Global prediction accuracy across all repos. */
    @GetMapping("/accuracy")
    public ResponseEntity<PredictionAccuracyDto> globalAccuracy() {
        Object[] row = outcomeRepository.getGlobalAccuracyStats();
        if (row == null || row.length == 0) {
            return ResponseEntity.ok(PredictionAccuracyDto.builder()
                    .totalPredictions(0).build());
        }
        // Native query returns Object[] — unpack carefully
        Object[] cols = (row[0] instanceof Object[]) ? (Object[]) row[0] : row;
        return ResponseEntity.ok(buildDto(null, cols));
    }

    /** Per-repo prediction accuracy. */
    @GetMapping("/accuracy/{owner}/{repo}")
    public ResponseEntity<PredictionAccuracyDto> repoAccuracy(
            @PathVariable String owner, @PathVariable String repo) {
        String fullName = owner + "/" + repo;
        return outcomeRepository.getAccuracyStats(fullName)
                .map(row -> {
                    Object[] cols = (row instanceof Object[]) ? (Object[]) row : new Object[]{row};
                    // First element is repo_full_name in the per-repo query
                    Object[] data = cols.length > 6 ? new Object[]{cols[1], cols[2], cols[3], cols[4], cols[5], cols[6]} : cols;
                    return ResponseEntity.ok(buildDto(fullName, data));
                })
                .orElseGet(() -> ResponseEntity.ok(PredictionAccuracyDto.builder()
                        .repoFullName(fullName)
                        .totalPredictions(0)
                        .build()));
    }

    private PredictionAccuracyDto buildDto(String repoFullName, Object[] cols) {
        int total = toInt(cols[0]);
        int resolved = toInt(cols[1]);
        int correct = toInt(cols[2]);
        int incorrect = toInt(cols[3]);
        int reverted = toInt(cols[4]);
        int hotfixed = toInt(cols[5]);
        Double accuracy = resolved > 0 ? Math.round(1000.0 * correct / resolved) / 10.0 : null;
        return PredictionAccuracyDto.builder()
                .repoFullName(repoFullName)
                .totalPredictions(total)
                .resolvedPredictions(resolved)
                .correctPredictions(correct)
                .incorrectPredictions(incorrect)
                .revertedCount(reverted)
                .hotfixedCount(hotfixed)
                .accuracyPct(accuracy)
                .build();
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        if (o instanceof BigDecimal bd) return bd.intValue();
        return 0;
    }
}
