package com.architect.controller;

import com.architect.config.AppProperties;
import com.architect.repository.RepoRepository;
import com.architect.service.GithubOAuthCallbackService;
import com.architect.service.PRAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/**
 * GitHub webhook entry for the real-time PR engine.
 * Flow: pull_request (opened/synchronize) → {@link PRAnalysisService} (async):
 * changed files → targeted scan at PR head → impact → PR comment → optional status + Slack.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final RepoRepository repoRepository;
    private final PRAnalysisService prAnalysisService;
    private final GithubOAuthCallbackService githubOAuthCallbackService;
    private final AppProperties appProperties;

    /**
     * GitHub redirects the browser here with GET when the OAuth "Authorization callback URL"
     * was mistakenly set to the webhook URL. Completes login instead of 405.
     * Webhook deliveries from GitHub remain POST only.
     */
    @GetMapping("/github")
    public Object githubBrowser(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error) {
        if (error != null && !error.isBlank()) {
            log.warn("GitHub OAuth error (callback may point at webhook URL): {}", error);
            return new RedirectView(appProperties.getFrontendUrl() + "/login?error=oauth_failed");
        }
        if (code != null && !code.isBlank()) {
            log.info("OAuth code received on webhook path — completing login (prefer callback URL /api/auth/callback in GitHub app settings)");
            return githubOAuthCallbackService.completeLogin(code);
        }
        String base = appProperties.getPublicBaseUrl() != null ? appProperties.getPublicBaseUrl() : "";
        String login = base + "/api/auth/github";
        String oauthCallback = appProperties.getGithub().getRedirectUri();
        String body = "Architect GitHub webhook (POST only for pull_request events).\n\n"
                + "Sign in: open " + login + " in your browser.\n\n"
                + "GitHub OAuth \"Authorization callback URL\" should be:\n  " + oauthCallback + "\n"
                + "Do not use this webhook URL as the OAuth callback unless GITHUB_REDIRECT_URI matches it.\n";
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(body);
    }

    @PostMapping("/github")
    public ResponseEntity<Void> handleGithubWebhook(
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "") String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String sigHeader,
            @RequestBody String rawBody) {

        String secret = appProperties.getGithub().getWebhookSecret();
        if (secret != null && !secret.isBlank()) {
            if (!isValidSignature(rawBody, sigHeader, secret)) {
                log.warn("Webhook signature mismatch — rejecting event '{}'", event);
                return ResponseEntity.status(401).build();
            }
        }

        log.info("Received GitHub webhook event: {}", event);

        if ("pull_request".equals(event)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(rawBody, Map.class);
                handlePullRequestEvent(payload);
            } catch (Exception e) {
                log.warn("Failed to parse webhook payload: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok().build();
    }

    private boolean isValidSignature(String body, String sigHeader, String secret) {
        if (sigHeader == null || !sigHeader.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String expectedHex = "sha256=" + HexFormat.of().formatHex(expected);
            return constantTimeEquals(expectedHex, sigHeader);
        } catch (Exception e) {
            log.warn("Signature validation error: {}", e.getMessage());
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    @SuppressWarnings("unchecked")
    private void handlePullRequestEvent(Map<String, Object> payload) {
        String action = (String) payload.get("action");
        if (!"opened".equals(action) && !"synchronize".equals(action)) return;

        Map<String, Object> prData = (Map<String, Object>) payload.get("pull_request");
        Map<String, Object> repoData = (Map<String, Object>) payload.get("repository");
        if (prData == null || repoData == null) return;

        int prNumber = ((Number) prData.get("number")).intValue();
        String prTitle = (String) prData.get("title");
        String fullName = (String) repoData.get("full_name");
        String prUrl = (String) prData.get("html_url");

        Map<String, Object> head = (Map<String, Object>) prData.get("head");
        String headSha = head != null ? (String) head.get("sha") : null;

        repoRepository.findByFullName(fullName).ifPresentOrElse(
            repo -> {
                prAnalysisService.processPullRequest(
                    repo, repo.getUser(), prNumber, headSha, prTitle, prUrl, fullName);
                log.info("Queued PR analysis for {} PR #{}", fullName, prNumber);
            },
            () -> log.debug("No Architect-connected repo for {}, skipping PR webhook", fullName)
        );
    }
}
