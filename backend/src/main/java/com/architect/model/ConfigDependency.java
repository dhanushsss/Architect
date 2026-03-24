package com.architect.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "config_dependencies")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigDependency {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id")
    private Repo repo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_repo_id")
    private Repo configRepo;

    @Column(name = "config_file", nullable = false)
    private String configFile;

    @Column(name = "referencing_file", nullable = false)
    private String referencingFile;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
