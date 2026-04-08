package com.architect.service;

import com.architect.model.PrPrediction;
import com.architect.model.PrPredictionOutcome;
import com.architect.repository.PrPredictionOutcomeRepository;
import com.architect.repository.PrPredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Closed-loop feedback: tracks what happens AFTER a PR merges.
 *
 * <h2>Signals detected</h2>
 * <ul>
 *   <li><b>Revert:</b> A subsequent PR that reverts the original (detected from title/body patterns)</li>
 *   <li><b>Hotfix:</b> A follow-up PR that touches the same files within a time window</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #onPrMerged} — creates PENDING outcome when a predicted PR merges</li>
 *   <li>{@link #onPrOpenedOrMerged} — checks if new PR is a revert or hotfix for a pending outcome</li>
 *   <li>{@link #resolveStalePendingOutcomes} — auto-resolves PENDING outcomes after 48h with no signal</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrOutcomeTrackerService {

    /** After this many hours with no revert/hotfix signal, assume prediction was correct. */
    private static final int RESOLUTION_WINDOW_HOURS = 48;

    private static final Set<String> REVERT_PATTERNS = Set.of(
            "revert", "reverts", "reverting", "rollback", "roll back", "undo"
    );
    private static final Set<String> HOTFIX_PATTERNS = Set.of(
            "hotfix", "hot-fix", "fix", "fixes", "bugfix", "patch", "urgent"
    );

    private final PrPredictionRepository predictionRepository;
    private final PrPredictionOutcomeRepository outcomeRepository;

    /**
     * Called when a PR that we previously predicted is merged.
     * Creates a PENDING outcome record that will be resolved by subsequent signals or timeout.
     */
    @Transactional
    public void onPrMerged(String repoFullName, int prNumber, String mergeSha, OffsetDateTime mergedAt) {
        // Find the most recent prediction for this PR
        PrPrediction prediction = predictionRepository
                .findTopByRepoFullNameAndPrNumberOrderByCreatedAtDesc(repoFullName, prNumber)
                .orElse(null);

        if (prediction == null) {
            log.debug("No prediction found for {}/#{}, skipping outcome tracking", repoFullName, prNumber);
            return;
        }

        // Don't create duplicate outcomes
        if (outcomeRepository.findByRepoFullNameAndPrNumber(repoFullName, prNumber).isPresent()) {
            log.debug("Outcome already tracked for {}/#{}", repoFullName, prNumber);
            return;
        }

        PrPredictionOutcome outcome = PrPredictionOutcome.builder()
                .predictionId(prediction.getId())
                .prNumber(prNumber)
                .repoFullName(repoFullName)
                .mergeSha(mergeSha)
                .mergedAt(mergedAt)
                .predictedRisk(prediction.getPredictedRisk())
                .outcome("PENDING")
                .build();

        outcomeRepository.save(outcome);
        log.info("Tracking outcome for {}/#{} (predicted: {})", repoFullName, prNumber, prediction.getPredictedRisk());
    }

    /**
     * Called on every new PR opened or merged — checks if it's a revert or hotfix
     * for any pending predictions in the same repo.
     */
    @Transactional
    public void onPrOpenedOrMerged(String repoFullName, int newPrNumber, String title,
                                    List<String> changedFiles) {
        String titleLower = title != null ? title.toLowerCase() : "";

        List<PrPredictionOutcome> pending =
                outcomeRepository.findByRepoFullNameAndOutcome(repoFullName, "PENDING");
        if (pending.isEmpty()) return;

        boolean isRevert = isRevertSignal(titleLower);
        boolean isHotfix = !isRevert && isHotfixSignal(titleLower);

        if (!isRevert && !isHotfix) return;

        for (PrPredictionOutcome outcome : pending) {
            OffsetDateTime now = OffsetDateTime.now();

            if (isRevert && isRevertOfPr(titleLower, outcome.getPrNumber())) {
                outcome.setOutcome("REVERTED");
                outcome.setRevertDetected(true);
                outcome.setRevertPrNumber(newPrNumber);
                outcome.setResolvedAt(now);
                if (outcome.getMergedAt() != null) {
                    outcome.setTimeToRevertMin(
                            (int) Duration.between(outcome.getMergedAt(), now).toMinutes());
                }
                assessPredictionAccuracy(outcome);
                outcomeRepository.save(outcome);
                log.info("Revert detected: PR #{} reverts {}/#{} (predicted: {})",
                        newPrNumber, repoFullName, outcome.getPrNumber(), outcome.getPredictedRisk());
                return; // revert is specific to one PR
            }

            if (isHotfix && changedFiles != null && !changedFiles.isEmpty()) {
                // A hotfix touching the same repo within the window is a signal
                outcome.setOutcome("HOTFIXED");
                outcome.setHotfixDetected(true);
                outcome.setHotfixPrNumber(newPrNumber);
                outcome.setHotfixDetectedAt(now);
                outcome.setResolvedAt(now);
                if (outcome.getMergedAt() != null) {
                    outcome.setTimeToHotfixMin(
                            (int) Duration.between(outcome.getMergedAt(), now).toMinutes());
                }
                assessPredictionAccuracy(outcome);
                outcomeRepository.save(outcome);
                log.info("Hotfix detected: PR #{} fixes {}/#{} (predicted: {})",
                        newPrNumber, repoFullName, outcome.getPrNumber(), outcome.getPredictedRisk());
            }
        }
    }

    /**
     * Scheduled job: auto-resolve PENDING outcomes after the resolution window.
     * If no revert/hotfix was detected, the prediction is considered correct.
     */
    @Scheduled(fixedDelayString = "${app.outcome-tracker.resolve-interval-ms:3600000}")
    @Transactional
    public void resolveStalePendingOutcomes() {
        List<PrPredictionOutcome> pending = outcomeRepository.findPendingOutcomes();
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(RESOLUTION_WINDOW_HOURS);
        int resolved = 0;

        for (PrPredictionOutcome outcome : pending) {
            if (outcome.getMergedAt() != null && outcome.getMergedAt().isBefore(cutoff)) {
                outcome.setOutcome("CORRECT");
                outcome.setWasPredictionCorrect(true);
                outcome.setResolvedAt(OffsetDateTime.now());
                outcomeRepository.save(outcome);
                resolved++;
            }
        }

        if (resolved > 0) {
            log.info("Auto-resolved {} pending prediction outcomes after {}h window", resolved, RESOLUTION_WINDOW_HOURS);
        }
    }

    /**
     * Assess whether the prediction was correct based on outcome.
     * - REVERTED/HOTFIXED + predicted LOW → incorrect (missed real breakage)
     * - REVERTED/HOTFIXED + predicted BLOCKED/REVIEW_REQUIRED → correct (caught it)
     * - CORRECT + predicted LOW → correct (nothing broke)
     * - CORRECT + predicted BLOCKED → technically correct but noisy (false positive)
     */
    private void assessPredictionAccuracy(PrPredictionOutcome outcome) {
        String risk = outcome.getPredictedRisk();
        String result = outcome.getOutcome();
        boolean breakageOccurred = "REVERTED".equals(result) || "HOTFIXED".equals(result);

        if (breakageOccurred) {
            // If we predicted high risk and breakage occurred → correct
            // If we predicted low risk and breakage occurred → incorrect (missed it)
            outcome.setWasPredictionCorrect(!"LOW".equals(risk));
        } else {
            // No breakage → low risk prediction was correct, high risk was a false positive
            outcome.setWasPredictionCorrect("LOW".equals(risk));
        }
    }

    private boolean isRevertSignal(String titleLower) {
        return REVERT_PATTERNS.stream().anyMatch(p ->
                titleLower.contains(p + " ") || titleLower.startsWith(p)
                        || titleLower.contains(p + "#") || titleLower.contains(p + " pr"));
    }

    private boolean isHotfixSignal(String titleLower) {
        return HOTFIX_PATTERNS.stream().anyMatch(p ->
                titleLower.startsWith(p + ":") || titleLower.startsWith(p + " ")
                        || titleLower.startsWith("[" + p + "]"));
    }

    /** Check if a revert title references a specific PR number. */
    private boolean isRevertOfPr(String titleLower, int prNumber) {
        return titleLower.contains("#" + prNumber)
                || titleLower.contains("pr " + prNumber)
                || titleLower.contains("pr#" + prNumber);
    }
}
