package com.architect.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_endpoints")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiEndpoint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id")
    private Repo repo;

    @Column(nullable = false)
    private String path;

    @Column(name = "http_method")
    private String httpMethod;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    private String framework;
    private String language;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
