package com.architect.service;

import com.architect.config.AppProperties;
import com.architect.dto.ImpactDto;
import com.architect.model.PrAnalysisRun;
import com.architect.model.Repo;
import com.architect.model.User;
import com.architect.repository.PrAnalysisRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Real-time PR engine: webhook → changed files → targeted scan (PR head only) →
 * map to graph (existing DB endpoints + extraction) → impact → PR comment → optional commit status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PRAnalysisService {

    private static final Set<String> SCANNABLE = Set.of(
        ".java", ".js", ".ts", ".jsx", ".tsx", ".py", ".rb", ".go", ".vue"
    );

    private final GitHubService gitHubService;
    private final ImpactAnalysisService impactAnalysisService;
    private final SlackNotificationService slackNotificationService;
    private final AppProperties appProperties;
    private final PrRiskEnrichmentService prRiskEnrichmentService;
    private final PrAnalysisRunRepository prAnalysisRunRepository;
    private final ObjectMapper objectMapper;

    @Async
    public void processPullRequest(Repo repo, User user, int prNumber, String headSha,
                                   String prTitle, String prUrl, String fullName) {
        String token = user.getAccessToken();
        String[] parts = fullName.split("/");
        String owner = parts[0];
        String repoName = parts[1];

        try {
            List<Map<String, Object>> prFiles = gitHubService.getPullRequest(token, owner, repoName, prNumber);
            List<String> changedPaths = prFiles.stream()
                .map(f -> (String) f.get("filename"))
                .filter(Objects::nonNull)
                .toList();

            int max = Math.max(1, appProperties.getPrEngine().getMaxChangedFilesToScan());
            Map<String, String> prHeadContents = new LinkedHashMap<>();
            if (headSha != null && !headSha.isBlank()) {
                for (String path : changedPaths) {
                    if (prHeadContents.size() >= max) break;
                    if (!isScannable(path)) continue;
                    String content = gitHubService.getFileContentAtRef(token, owner, repoName, path, headSha);
                    if (!content.isBlank()) {
                        prHeadContents.put(path, content);
                    }
                }
            }

            log.info("PR #{} targeted scan: {} changed files, {} fetched for extraction",
                prNumber, changedPaths.size(), prHeadContents.size());

            ImpactDto impact = impactAnalysisService.analyzePullRequestTargeted(repo, changedPaths, prHeadContents);
            PrCommentFormatter.Scenario scenario = PrCommentFormatter.classify(impact);
            prRiskEnrichmentService.enrich(impact, repo, scenario, changedPaths.size(), prHeadContents.size());

            String frontend = appProperties.getFrontendUrl() != null
                ? appProperties.getFrontendUrl().replaceAll("/$", "")
                : "http://localhost:3000";
            String comment = PrCommentFormatter.buildComment(scenario, impact, changedPaths.size(), prUrl, frontend);
            log.info("PR #{} comment scenario={}", prNumber, scenario);

            persistPrRun(user, repo, prNumber, prUrl, headSha, scenario, impact);
            gitHubService.createPrComment(token, owner, repoName, prNumber, comment);

            if (appProperties.getPrEngine().isPostCommitStatus() && headSha != null && !headSha.isBlank()) {
                postStatus(token, owner, repoName, headSha, impact, prUrl, frontend);
            }

            if (!"SAFE TO MERGE".equals(impact.getVerdict())) {
                slackNotificationService.sendImpactAlert(fullName, prTitle, impact);
            }

            log.info("PR #{} Architect: verdict={} score={} repos={}",
                prNumber, impact.getVerdict(), impact.getNumericScore(), impact.getDependentsCount());
        } catch (Exception e) {
            log.error("PR analysis failed for {}/#{}", fullName, prNumber, e);
        }
    }

    private void postStatus(String token, String owner, String repoName, String sha,
                            ImpactDto impact, String prUrl, String frontend) {
        boolean strict = appProperties.getPrEngine().isFailOnReviewRequired();
        String state;
        if ("BLOCKED".equals(impact.getVerdict())) {
            state = "failure";
        } else if ("REVIEW REQUIRED".equals(impact.getVerdict()) && strict) {
            state = "failure";
        } else {
            state = "success";
        }
        String desc = "Architect: " + impact.getVerdict() + " (" + String.format("%.1f", impact.getNumericScore()) + "/10)";
        gitHubService.createCommitStatus(token, owner, repoName, sha, state, desc, prUrl,
            "architect/pr-impact");
    }

    private void persistPrRun(User user, Repo repo, int prNumber, String prUrl, String headSha,
                              PrCommentFormatter.Scenario scenario, ImpactDto impact) {
        try {
            String names = impact.getAffectedRepos() == null ? ""
                : impact.getAffectedRepos().stream().map(ImpactDto.AffectedItem::getName).collect(Collectors.joining(","));
            String factorsJson = impact.getRiskFactors() == null ? "[]"
                : objectMapper.writeValueAsString(impact.getRiskFactors());
            prAnalysisRunRepository.save(PrAnalysisRun.builder()
                .user(user)
                .repo(repo)
                .githubPrNumber(prNumber)
                .prUrl(prUrl)
                .headSha(headSha)
                .scenario(scenario.name())
                .verdict(impact.getVerdict())
                .numericScore(impact.getNumericScore())
                .dependentsCount(impact.getDependentsCount())
                .confidenceScore(impact.getConfidenceScore())
                .affectedRepoNames(names)
                .riskFactorsJson(factorsJson)
                .build());
        } catch (Exception e) {
            log.warn("Could not persist PR analysis run: {}", e.getMessage());
        }
    }

    private static boolean isScannable(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return SCANNABLE.stream().anyMatch(lower::endsWith);
    }

}
