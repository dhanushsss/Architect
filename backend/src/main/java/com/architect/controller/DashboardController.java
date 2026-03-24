package com.architect.controller;

import com.architect.config.AppProperties;
import com.architect.model.PrAnalysisRun;
import com.architect.model.User;
import com.architect.repository.PrAnalysisRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Phase 1–3: product shell — risky PRs this week, webhook URL, UI flags.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final AppProperties appProperties;
    private final PrAnalysisRunRepository prAnalysisRunRepository;

    @GetMapping("/product-config")
    public ResponseEntity<Map<String, Object>> productConfig() {
        String base = appProperties.getPublicBaseUrl() != null
            ? appProperties.getPublicBaseUrl().replaceAll("/$", "") : "http://localhost:8080";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("coreOnly", appProperties.getUi().isCoreOnly());
        m.put("webhookUrl", base + "/api/webhooks/github");
        m.put("webhookEvents", List.of("pull_request"));
        m.put("frontendUrl", appProperties.getFrontendUrl());
        m.put("features", Map.of(
            "prEngine", true,
            "commitStatus", appProperties.getPrEngine().isPostCommitStatus(),
            "slackAlerts", appProperties.getSlack().getWebhookUrl() != null
                && !appProperties.getSlack().getWebhookUrl().isBlank()
        ));
        return ResponseEntity.ok(m);
    }

    @GetMapping("/risky-prs-week")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> riskyPrsThisWeek(@AuthenticationPrincipal User user) {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<PrAnalysisRun> runs = prAnalysisRunRepository.findRiskySince(user, since);
        List<Map<String, Object>> out = runs.stream().limit(25).map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("repoName", r.getRepo().getName());
            row.put("repoFullName", r.getRepo().getFullName());
            row.put("prNumber", r.getGithubPrNumber());
            row.put("prUrl", r.getPrUrl());
            row.put("scenario", r.getScenario());
            row.put("verdict", r.getVerdict());
            row.put("numericScore", r.getNumericScore());
            row.put("dependentsCount", r.getDependentsCount());
            row.put("confidenceScore", r.getConfidenceScore());
            row.put("affectedRepoNames", r.getAffectedRepoNames());
            row.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
            return row;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(out);
    }
}
