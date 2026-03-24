package com.architect.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "repos")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Repo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "github_id", nullable = false)
    private Long githubId;

    @Column(nullable = false)
    private String name;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String description;

    @Column(name = "default_branch")
    private String defaultBranch;

    @Column(name = "primary_language")
    private String primaryLanguage;

    @Column(name = "html_url")
    private String htmlUrl;

    @Column(name = "is_private")
    private Boolean isPrivate;

    @Column(name = "scan_status")
    @Enumerated(EnumType.STRING)
    private ScanStatus scanStatus;

    @Column(name = "last_scanned_at")
    private LocalDateTime lastScannedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (scanStatus == null) scanStatus = ScanStatus.PENDING;
    }

    public enum ScanStatus {
        PENDING, SCANNING, COMPLETE, FAILED
    }
}
