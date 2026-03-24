package com.architect.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "dependency_edges")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DependencyEdge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "edge_type", nullable = false)
    private String edgeType;

    private String label;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    /** INTERNAL | MONOREPO | EXTERNAL (for IMPORTS edges) */
    @Column(name = "import_type", length = 20)
    private String importType;

    @Column(name = "source_file", length = 500)
    private String sourceFile;

    @Column(name = "target_file", length = 500)
    private String targetFile;

    @Column(name = "package_path", length = 500)
    private String packagePath;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
