package com.architect.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "pr_predictions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(name = "repo_full_name", nullable = false, length = 255)
    private String repoFullName;

    @Column(name = "predicted_risk", nullable = false, length = 20)
    private String predictedRisk;

    @Column(name = "confidence_pct", nullable = false)
    private Integer confidencePct;

    @Column(name = "direct_match_count", nullable = false)
    private Integer directMatchCount;

    @Column(name = "inferred_match_count", nullable = false)
    private Integer inferredMatchCount;

    @Column(name = "unresolved_call_count", nullable = false)
    private Integer unresolvedCallCount;

    @Column(name = "stale_repo_count", nullable = false)
    private Integer staleRepoCount;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "affected_repo_names", columnDefinition = "text[]")
    private String[] affectedRepoNames;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signal_breakdown", columnDefinition = "jsonb")
    private String signalBreakdown;

    @Column(name = "pr_head_sha", length = 40)
    private String prHeadSha;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
