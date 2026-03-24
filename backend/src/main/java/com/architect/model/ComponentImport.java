package com.architect.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "component_imports")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComponentImport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_repo_id")
    private Repo sourceRepo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_repo_id")
    private Repo targetRepo;

    @Column(name = "component_name")
    private String componentName;

    @Column(name = "import_path", nullable = false)
    private String importPath;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    /** INTERNAL | MONOREPO | EXTERNAL */
    @Column(name = "import_type", length = 20)
    private String importType;

    /** Resolved file path for INTERNAL/MONOREPO imports */
    @Column(name = "resolved_file", length = 500)
    private String resolvedFile;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
