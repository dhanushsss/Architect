package com.architect.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "organizations")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Organization {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String slug;

    private String plan = "FREE";

    @Column(name = "sso_enabled")
    private boolean ssoEnabled = false;

    @Column(name = "saml_idp_url")
    private String samlIdpUrl;

    @Column(name = "saml_cert", columnDefinition = "TEXT")
    private String samlCert;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
