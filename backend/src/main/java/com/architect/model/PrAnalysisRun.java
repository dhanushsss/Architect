package com.architect.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pr_analysis_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrAnalysisRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private Repo repo;

    @Column(name = "github_pr_number", nullable = false)
    private Integer githubPrNumber;

    @Column(name = "pr_url")
    private String prUrl;

    @Column(name = "head_sha", length = 64)
    private String headSha;

    @Column(name = "scenario", length = 64)
    private String scenario;

    @Column(name = "verdict", length = 32)
    private String verdict;

    @Column(name = "numeric_score")
    private Double numericScore;

    @Column(name = "dependents_count")
    private Integer dependentsCount;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "affected_repo_names", columnDefinition = "TEXT")
    private String affectedRepoNames;

    @Column(name = "risk_factors_json", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String riskFactorsJson;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
