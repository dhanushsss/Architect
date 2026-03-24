package com.architect.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_calls")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiCall {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caller_repo_id")
    private Repo callerRepo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id")
    private ApiEndpoint endpoint;

    @Column(name = "url_pattern", nullable = false)
    private String urlPattern;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "call_type")
    private String callType;

    /** GET, POST, … from axios/fetch/WebClient */
    @Column(name = "http_method", length = 20)
    private String httpMethod;

    /** Path after normalization (e.g. /api/users/*) for matching */
    @Column(name = "normalized_pattern", columnDefinition = "TEXT")
    private String normalizedPattern;

    /** INTERNAL_ENDPOINT | EXTERNAL | UNRESOLVED */
    @Column(name = "target_kind", length = 30)
    private String targetKind;

    @Column(name = "external_host", length = 255)
    private String externalHost;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
