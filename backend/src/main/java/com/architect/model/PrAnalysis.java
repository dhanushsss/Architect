package com.architect.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "pr_analyses")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrAnalysis {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id")
    private Repo repo;

    @Column(name = "pr_number")
    private Integer prNumber;

    @Column(name = "pr_title")
    private String prTitle;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "affected_repos")
    private List<String> affectedRepos;

    @Column(name = "risk_score")
    private String riskScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "analysis_json")
    private Map<String, Object> analysisJson;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
