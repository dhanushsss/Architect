package com.architect.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "ai_query_history")
public class AiQueryHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "query_text", nullable = false)
    private String queryText;

    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;

    @Column(name = "query_type")
    private String queryType = "NL_QUERY";

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
