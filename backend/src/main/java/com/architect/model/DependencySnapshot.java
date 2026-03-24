package com.architect.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "dependency_snapshots")
public class DependencySnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "snapshot_label")
    private String snapshotLabel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_data", columnDefinition = "jsonb", nullable = false)
    private String snapshotData;

    @Column(name = "node_count")
    private int nodeCount;

    @Column(name = "edge_count")
    private int edgeCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
