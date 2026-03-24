package com.architect.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private GitHub github = new GitHub();
    private Jwt jwt = new Jwt();
    private String frontendUrl;
    /** Semantic product version (UI + /api/public/version) */
    private String productVersion = "1.1.0";
    private Slack slack = new Slack();
    private Anthropic anthropic = new Anthropic();
    /** Real-time PR engine: targeted scan + impact comment */
    private PrEngine prEngine = new PrEngine();
    /** Public URL for webhook docs (e.g. https://api.yourcompany.com) */
    private String publicBaseUrl = "http://localhost:8080";
    private Ui ui = new Ui();

    @Data
    public static class Ui {
        /** Phase 1: hide graph-first, AI chat, governance, API keys in UI */
        private boolean coreOnly = false;
    }

    @Data
    public static class PrEngine {
        /** Max changed files to fetch + parse per PR (performance) */
        private int maxChangedFilesToScan = 50;
        /** POST GitHub commit status on PR head (requires repo scope) */
        private boolean postCommitStatus = false;
        /** If true, mark commit failed when verdict is REVIEW REQUIRED or BLOCKED */
        private boolean failOnReviewRequired = false;
    }

    @Data
    public static class GitHub {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
    }

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMs;
    }

    @Data
    public static class Slack {
        private String webhookUrl;
    }

    @Data
    public static class Anthropic {
        private String apiKey;
    }
}
