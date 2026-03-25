package com.architect.model;

import com.architect.scan.ScanType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "scan_tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanQueueTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repo_id")
    private Repo repo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScanType type;

    @Column(name = "scan_mode", nullable = false, length = 16)
    private String scanMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScanTaskStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "picked_by", length = 64)
    private String pickedBy;

    @Column(name = "picked_at")
    private Instant pickedAt;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = ScanTaskStatus.QUEUED;
        }
    }
}
