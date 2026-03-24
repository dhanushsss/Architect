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
            @RequestBody Map<String, Object> payload) {

        log.info("Received GitHub webhook event: {}", event);

        if ("pull_request".equals(event)) {
            handlePullRequestEvent(payload);
        }

        return ResponseEntity.ok().build();
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
