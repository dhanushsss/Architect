package com.architect.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "runtime_wiring_facts")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuntimeWiringFact {

    public static final String APP_NAME = "APP_NAME";
    public static final String SERVER_PORT = "SERVER_PORT";
    public static final String EUREKA_REGISTRY = "EUREKA_REGISTRY";
    public static final String GATEWAY_ROUTE = "GATEWAY_ROUTE";
    public static final String VITE_PROXY = "VITE_PROXY";
    public static final String ENV_SERVICE_URL = "ENV_SERVICE_URL";
    /** Outbound backend host: Java HTTP client, Feign name, or yml service URL */
    public static final String BACKEND_HOST = "BACKEND_HOST";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private Repo repo;

    @Column(name = "fact_type", nullable = false, length = 40)
    private String factType;

    @Column(name = "fact_key", length = 500)
    private String factKey;

    @Column(name = "fact_value", columnDefinition = "TEXT")
    private String factValue;

    @Column(name = "source_file", nullable = false, columnDefinition = "TEXT")
    private String sourceFile;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
