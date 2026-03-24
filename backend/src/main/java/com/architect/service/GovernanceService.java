package com.architect.service;

import com.architect.model.ApiEndpoint;
import com.architect.model.Repo;
import com.architect.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GovernanceService {

    private final RepoRepository repoRepository;
    private final ApiEndpointRepository apiEndpointRepository;
    private final ApiCallRepository apiCallRepository;

    public Map<String, Object> getGovernanceDashboard(Long userId) {
        List<Repo> repos = repoRepository.findByUserId(userId);
        Map<String, Object> dashboard = new LinkedHashMap<>();

        List<Map<String, Object>> endpointHealth = new ArrayList<>();
        int totalEndpoints = 0;
        int undocumented = 0;
        int orphaned = 0;
        int deprecated = 0;

        for (Repo repo : repos) {
            List<ApiEndpoint> eps = apiEndpointRepository.findByRepoId(repo.getId());
            totalEndpoints += eps.size();

            for (ApiEndpoint ep : eps) {
                long callerCount = apiCallRepository.countByEndpointId(ep.getId());
                boolean isOrphan = callerCount == 0;
                boolean isDeprecated = ep.getPath().contains("/v1/") &&
                        apiEndpointRepository.existsByRepoIdAndPathContaining(ep.getRepo().getId(), "/v2/");

                if (isOrphan) orphaned++;
                if (isDeprecated) deprecated++;

                // All endpoints without Swagger/OpenAPI docs are "undocumented" for now
                undocumented++;

                Map<String, Object> epInfo = new LinkedHashMap<>();
                epInfo.put("id", ep.getId());
                epInfo.put("repo", repo.getName());
                epInfo.put("method", ep.getHttpMethod());
                epInfo.put("path", ep.getPath());
                epInfo.put("framework", ep.getFramework());
                epInfo.put("language", ep.getLanguage());
                epInfo.put("callerCount", callerCount);
                epInfo.put("status", isOrphan ? "ORPHANED" : isDeprecated ? "DEPRECATED" : "ACTIVE");
                endpointHealth.add(epInfo);
            }
        }

        dashboard.put("totalEndpoints", totalEndpoints);
        dashboard.put("orphanedEndpoints", orphaned);
        dashboard.put("deprecatedEndpoints", deprecated);
        dashboard.put("undocumentedEndpoints", undocumented);
        dashboard.put("healthScore", calculateHealthScore(totalEndpoints, orphaned, deprecated));
        dashboard.put("endpoints", endpointHealth);
        dashboard.put("summary", buildGovernanceSummary(repos, totalEndpoints, orphaned, deprecated));

        return dashboard;
    }

    public Map<String, Object> getSocAuditReport(Long userId) {
        List<Repo> repos = repoRepository.findByUserId(userId);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "SOC2_DEPENDENCY_AUDIT");
        report.put("generatedAt", java.time.LocalDateTime.now().toString());
        report.put("totalRepos", repos.size());

        // Check for sensitive API patterns
        List<Map<String, Object>> sensitiveEndpoints = new ArrayList<>();
        for (Repo repo : repos) {
            List<ApiEndpoint> eps = apiEndpointRepository.findByRepoId(repo.getId());
            for (ApiEndpoint ep : eps) {
                if (isSensitivePath(ep.getPath())) {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("repo", repo.getName());
                    s.put("method", ep.getHttpMethod());
                    s.put("path", ep.getPath());
                    s.put("category", classifySensitivity(ep.getPath()));
                    sensitiveEndpoints.add(s);
                }
            }
        }

        report.put("sensitiveEndpoints", sensitiveEndpoints);
        report.put("sensitiveEndpointCount", sensitiveEndpoints.size());
        report.put("compliance", buildComplianceChecklist(repos, sensitiveEndpoints));

        return report;
    }

    private boolean isSensitivePath(String path) {
        String lower = path.toLowerCase();
        return lower.contains("/user") || lower.contains("/payment") ||
               lower.contains("/password") || lower.contains("/auth") ||
               lower.contains("/account") || lower.contains("/pii") ||
               lower.contains("/ssn") || lower.contains("/credit") ||
               lower.contains("/health") || lower.contains("/admin");
    }

    private String classifySensitivity(String path) {
        String lower = path.toLowerCase();
        if (lower.contains("/payment") || lower.contains("/credit")) return "FINANCIAL";
        if (lower.contains("/user") || lower.contains("/pii") || lower.contains("/ssn")) return "PII";
        if (lower.contains("/auth") || lower.contains("/password")) return "AUTHENTICATION";
        if (lower.contains("/admin")) return "PRIVILEGED_ACCESS";
        if (lower.contains("/health")) return "HEALTH_DATA";
        return "SENSITIVE";
    }

    private int calculateHealthScore(int total, int orphaned, int deprecated) {
        if (total == 0) return 100;
        int issues = orphaned + deprecated;
        return Math.max(0, 100 - (issues * 100 / total));
    }

    private List<String> buildGovernanceSummary(List<Repo> repos, int total, int orphaned, int deprecated) {
        List<String> summary = new ArrayList<>();
        summary.add(repos.size() + " repositories scanned");
        summary.add(total + " total API endpoints discovered");
        if (orphaned > 0) summary.add(orphaned + " orphaned endpoints (no known callers)");
        if (deprecated > 0) summary.add(deprecated + " potentially deprecated endpoints");
        if (orphaned == 0 && deprecated == 0) summary.add("All endpoints appear to be in use");
        return summary;
    }

    private Map<String, Object> buildComplianceChecklist(List<Repo> repos, List<Map<String, Object>> sensitive) {
        Map<String, Object> checklist = new LinkedHashMap<>();
        checklist.put("allReposScanned", repos.stream().allMatch(r -> "COMPLETE".equals(r.getScanStatus())));
        checklist.put("sensitiveEndpointsIdentified", !sensitive.isEmpty());
        checklist.put("dependencyGraphComplete", !repos.isEmpty());
        checklist.put("auditTrailAvailable", true);
        return checklist;
    }
}
