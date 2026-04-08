package com.architect.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Tracks the real-world outcome of a PR prediction — did the prediction match reality?
 * Populated by {@link com.architect.service.PrOutcomeTrackerService} from webhook events.
 */
@Entity
@Table(name = "pr_prediction_outcomes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrPredictionOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prediction_id")
    private Long predictionId;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(name = "repo_full_name", nullable = false, length = 255)
    private String repoFullName;

    @Column(name = "merge_sha", length = 40)
    private String mergeSha;

    @Column(name = "merged_at")
    private OffsetDateTime mergedAt;

    @Column(name = "outcome", nullable = false, length = 30)
    @Builder.Default
    private String outcome = "PENDING";

    @Column(name = "revert_pr_number")
    private Integer revertPrNumber;

    @Column(name = "hotfix_pr_number")
    private Integer hotfixPrNumber;

    @Column(name = "hotfix_detected_at")
    private OffsetDateTime hotfixDetectedAt;

    @Column(name = "revert_detected", nullable = false)
    @Builder.Default
    private Boolean revertDetected = false;

    @Column(name = "hotfix_detected", nullable = false)
    @Builder.Default
    private Boolean hotfixDetected = false;

    @Column(name = "time_to_revert_min")
    private Integer timeToRevertMin;

    @Column(name = "time_to_hotfix_min")
    private Integer timeToHotfixMin;

    @Column(name = "incident_url")
    private String incidentUrl;

    @Column(name = "predicted_risk", length = 20)
    private String predictedRisk;

    @Column(name = "was_prediction_correct")
    private Boolean wasPredictionCorrect;

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
}
