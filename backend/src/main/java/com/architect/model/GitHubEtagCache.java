package com.architect.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "github_etag_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class GitHubEtagCache {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private Repo repo;

    @Column(name = "resource_path", nullable = false, length = 768)
    private String resourcePath;

    @Column(length = 255)
    private String etag;

    @Column(name = "cached_body", columnDefinition = "TEXT")
    private String cachedBody;

    @Column(name = "last_fetched_at")
    private LocalDateTime lastFetchedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        if (lastFetchedAt == null) {
            lastFetchedAt = LocalDateTime.now();
        }
    }
}
