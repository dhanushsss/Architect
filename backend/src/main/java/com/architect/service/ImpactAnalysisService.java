package com.architect.service;

import com.architect.dto.ImpactDto;
import com.architect.model.*;
import com.architect.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ImpactAnalysisService {

    private final ApiEndpointRepository apiEndpointRepository;
    private final ApiCallRepository apiCallRepository;
    private final ComponentImportRepository componentImportRepository;
    private final RepoRepository repoRepository;
    private final EndpointExtractorService endpointExtractorService;

    // ── public API ────────────────────────────────────────────────────────────

    public ImpactDto analyzeEndpoint(Long endpointId) {
        ApiEndpoint endpoint = apiEndpointRepository.findById(endpointId)
            .orElseThrow(() -> new RuntimeException("Endpoint not found: " + endpointId));

        List<ApiCall> callers = apiCallRepository.findByEndpoint(endpoint);
        Set<String> affectedRepoIds = new HashSet<>();
        List<ImpactDto.AffectedItem> affectedFiles = new ArrayList<>();
        List<ImpactDto.AffectedItem> affectedRepos = new ArrayList<>();

        for (ApiCall call : callers) {
            Repo callerRepo = call.getCallerRepo();
            if (callerRepo != null) {
                String repoId = String.valueOf(callerRepo.getId());
                if (affectedRepoIds.add(repoId)) {
                    affectedRepos.add(ImpactDto.AffectedItem.builder()
                        .id(repoId)
                        .name(callerRepo.getName())
                        .type("REPO")
                        .detail(callerRepo.getPrimaryLanguage())
                        .build());
                }
                affectedFiles.add(ImpactDto.AffectedItem.builder()
                    .id(String.valueOf(call.getId()))
                    .name(call.getFilePath())
                    .type("FILE")
                    .detail("Line " + call.getLineNumber() + " (" + call.getCallType() + ")")
                    .build());
            }
        }

        RiskResult risk = computeRisk(affectedRepoIds.size(), affectedFiles.size(), 1);

        return ImpactDto.builder()
            .subjectId(String.valueOf(endpointId))
            .subjectType("API_ENDPOINT")
            .subjectLabel(endpoint.getHttpMethod() + " " + endpoint.getPath())
            .riskScore(risk.label())
            .numericScore(risk.score())
            .verdict(risk.verdict())
            .dependentsCount(affectedRepoIds.size())
            .affectedRepos(affectedRepos)
            .affectedFiles(affectedFiles)
            .build();
    }

    public ImpactDto analyzeRepo(Long repoId) {
        Repo repo = repoRepository.findById(repoId)
            .orElseThrow(() -> new RuntimeException("Repo not found: " + repoId));

        List<ComponentImport> importers = componentImportRepository.findByTargetRepo(repo);
        Set<String> affectedRepoIds = new HashSet<>();
        List<ImpactDto.AffectedItem> affectedRepos = new ArrayList<>();
        List<ImpactDto.AffectedItem> affectedFiles = new ArrayList<>();

        for (ComponentImport imp : importers) {
            Repo sourceRepo = imp.getSourceRepo();
            if (sourceRepo != null) {
                if (affectedRepoIds.add(String.valueOf(sourceRepo.getId()))) {
                    affectedRepos.add(ImpactDto.AffectedItem.builder()
                        .id(String.valueOf(sourceRepo.getId()))
                        .name(sourceRepo.getName())
                        .type("REPO")
                        .detail("imports " + imp.getComponentName())
                        .build());
                }
                affectedFiles.add(ImpactDto.AffectedItem.builder()
                    .id(String.valueOf(imp.getId()))
                    .name(imp.getFilePath())
                    .type("FILE")
                    .detail("imports " + imp.getImportPath())
                    .build());
            }
        }

        // Also check repos that call APIs exposed by this repo
        List<ApiEndpoint> endpoints = apiEndpointRepository.findByRepo(repo);
        List<String> orphanEndpoints = new ArrayList<>();
        int calledEndpointCount = 0;
        for (ApiEndpoint ep : endpoints) {
            long callerCount = apiCallRepository.countByEndpoint(ep);
            if (callerCount == 0) {
                orphanEndpoints.add(ep.getHttpMethod() + " " + ep.getPath() + " (" + ep.getFilePath() + ")");
            } else {
                calledEndpointCount++;
            }
        }

        RiskResult risk = computeRisk(affectedRepoIds.size(), affectedFiles.size(), calledEndpointCount);

        return ImpactDto.builder()
            .subjectId(String.valueOf(repoId))
            .subjectType("REPO")
            .subjectLabel(repo.getName())
            .riskScore(risk.label())
            .numericScore(risk.score())
            .verdict(risk.verdict())
            .dependentsCount(affectedRepoIds.size())
            .affectedRepos(affectedRepos)
            .affectedFiles(affectedFiles)
            .orphanEndpoints(orphanEndpoints)
            .build();
    }

    /**
     * PR-specific: given the list of changed file paths in a PR, find which known
     * API endpoints live in those files and which downstream repos call them.
     */
    public ImpactDto analyzeChangedFiles(List<String> changedFilePaths, Long repoId) {
        Repo repo = repoRepository.findById(repoId)
            .orElseThrow(() -> new RuntimeException("Repo not found: " + repoId));

        Set<String> changedPathSet = new HashSet<>(changedFilePaths);
        List<ApiEndpoint> changedEndpoints = apiEndpointRepository.findByRepo(repo)
            .stream()
            .filter(ep -> changedPathSet.contains(ep.getFilePath()))
            .toList();

        Set<String> affectedRepoIds = new HashSet<>();
        List<ImpactDto.AffectedItem> affectedRepos = new ArrayList<>();
        List<ImpactDto.AffectedItem> affectedFiles = new ArrayList<>();
        List<String> changedEpLabels = new ArrayList<>();

        for (ApiEndpoint ep : changedEndpoints) {
            changedEpLabels.add(ep.getHttpMethod() + " " + ep.getPath());
            for (ApiCall call : apiCallRepository.findByEndpoint(ep)) {
                Repo callerRepo = call.getCallerRepo();
                if (callerRepo != null && !callerRepo.getId().equals(repoId)) {
                    String rid = String.valueOf(callerRepo.getId());
                    if (affectedRepoIds.add(rid)) {
                        affectedRepos.add(ImpactDto.AffectedItem.builder()
                            .id(rid)
                            .name(callerRepo.getName())
                            .type("REPO")
                            .detail("calls " + ep.getHttpMethod() + " " + ep.getPath())
                            .build());
                    }
                    affectedFiles.add(ImpactDto.AffectedItem.builder()
                        .id(String.valueOf(call.getId()))
                        .name(call.getFilePath())
                        .type("FILE")
                        .detail(callerRepo.getName() + ":" + call.getLineNumber())
                        .build());
                }
            }
        }

        // Also include component importers for changed files
        for (ComponentImport imp : componentImportRepository.findByTargetRepo(repo)) {
            if (imp.getSourceRepo() != null) {
                String rid = String.valueOf(imp.getSourceRepo().getId());
                if (affectedRepoIds.add(rid)) {
                    affectedRepos.add(ImpactDto.AffectedItem.builder()
                        .id(rid)
                        .name(imp.getSourceRepo().getName())
                        .type("REPO")
                        .detail("imports component from this repo")
                        .build());
                }
            }
        }

        RiskResult risk = computeRisk(affectedRepoIds.size(), affectedFiles.size(), changedEndpoints.size());

        return ImpactDto.builder()
            .subjectId(String.valueOf(repoId))
            .subjectType("REPO")
            .subjectLabel(repo.getName())
            .riskScore(risk.label())
            .numericScore(risk.score())
            .verdict(risk.verdict())
            .dependentsCount(affectedRepoIds.size())
            .affectedRepos(affectedRepos)
            .affectedFiles(affectedFiles)
            .changedEndpoints(changedEpLabels)
            .build();
    }

    /**
     * PR engine: combine (1) DB endpoints in changed paths with (2) endpoints extracted from PR head
     * file contents, then aggregate cross-repo callers. Does not re-scan the full repo.
     */
    public ImpactDto analyzePullRequestTargeted(Repo repo, List<String> changedFilePaths,
                                                Map<String, String> prHeadContentsByPath) {
        Set<String> changedPathSet = new HashSet<>(changedFilePaths);
        Set<Long> endpointIds = new LinkedHashSet<>();
        LinkedHashSet<String> changedEpLabels = new LinkedHashSet<>();

        for (ApiEndpoint ep : apiEndpointRepository.findByRepo(repo)) {
            if (changedPathSet.contains(ep.getFilePath())) {
                endpointIds.add(ep.getId());
                changedEpLabels.add(ep.getHttpMethod() + " " + ep.getPath());
            }
        }

        for (Map.Entry<String, String> e : prHeadContentsByPath.entrySet()) {
            String path = e.getKey();
            String content = e.getValue();
            if (content == null || content.length() > 512_000) continue;
            for (EndpointExtractorService.ExtractedEndpoint ex : endpointExtractorService.extract(content, path)) {
                if (ex.getPath() == null || ex.getHttpMethod() == null) continue;
                changedEpLabels.add(ex.getHttpMethod() + " " + ex.getPath());
                for (ApiEndpoint dbEp : apiEndpointRepository.findByRepo(repo)) {
                    if (pathsMatchForPr(dbEp.getPath(), ex.getPath()) && methodsMatch(dbEp.getHttpMethod(), ex.getHttpMethod())) {
                        endpointIds.add(dbEp.getId());
                        break;
                    }
                }
            }
        }

        Set<String> affectedRepoIds = new HashSet<>();
        List<ImpactDto.AffectedItem> affectedRepos = new ArrayList<>();
        List<ImpactDto.AffectedItem> affectedFiles = new ArrayList<>();

        for (Long epId : endpointIds) {
            ApiEndpoint ep = apiEndpointRepository.findById(epId).orElse(null);
            if (ep == null) continue;
            for (ApiCall call : apiCallRepository.findByEndpoint(ep)) {
                Repo callerRepo = call.getCallerRepo();
                if (callerRepo != null && !callerRepo.getId().equals(repo.getId())) {
                    String rid = String.valueOf(callerRepo.getId());
                    if (affectedRepoIds.add(rid)) {
                        affectedRepos.add(ImpactDto.AffectedItem.builder()
                            .id(rid)
                            .name(callerRepo.getName())
                            .type("REPO")
                            .detail("calls " + ep.getHttpMethod() + " " + ep.getPath())
                            .build());
                    }
                    affectedFiles.add(ImpactDto.AffectedItem.builder()
                        .id(String.valueOf(call.getId()))
                        .name(call.getFilePath())
                        .type("FILE")
                        .detail(callerRepo.getName() + ":" + call.getLineNumber())
                        .build());
                }
            }
        }

        LinkedHashSet<String> prOrphanEndpoints = new LinkedHashSet<>();
        for (Long epId : endpointIds) {
            ApiEndpoint ep = apiEndpointRepository.findById(epId).orElse(null);
            if (ep == null) continue;
            if (!endpointHasExternalCallers(ep, repo.getId())) {
                prOrphanEndpoints.add(ep.getHttpMethod() + " " + ep.getPath());
            }
        }
        for (String label : changedEpLabels) {
            boolean hasExternalCaller = false;
            for (Long epId : endpointIds) {
                ApiEndpoint ep = apiEndpointRepository.findById(epId).orElse(null);
                if (ep == null) continue;
                if (labelMatchesEndpointLabel(label, ep) && endpointHasExternalCallers(ep, repo.getId())) {
                    hasExternalCaller = true;
                    break;
                }
            }
            if (!hasExternalCaller) {
                prOrphanEndpoints.add(label);
            }
        }

        int epCount = Math.max(changedEpLabels.size(), endpointIds.size());
        RiskResult risk;
        if (affectedRepoIds.isEmpty() && !changedEpLabels.isEmpty()) {
            risk = new RiskResult(4.0, "MEDIUM", "REVIEW REQUIRED");
        } else {
            risk = computeRisk(affectedRepoIds.size(), affectedFiles.size(), epCount);
        }

        return ImpactDto.builder()
            .subjectId(String.valueOf(repo.getId()))
            .subjectType("REPO")
            .subjectLabel(repo.getName())
            .riskScore(risk.label())
            .numericScore(risk.score())
            .verdict(risk.verdict())
            .dependentsCount(affectedRepoIds.size())
            .affectedRepos(affectedRepos)
            .affectedFiles(affectedFiles)
            .changedEndpoints(new ArrayList<>(changedEpLabels))
            .prOrphanEndpoints(new ArrayList<>(prOrphanEndpoints))
            .build();
    }

    private boolean endpointHasExternalCallers(ApiEndpoint ep, Long sourceRepoId) {
        return apiCallRepository.findByEndpoint(ep).stream()
            .anyMatch(c -> c.getCallerRepo() != null && !c.getCallerRepo().getId().equals(sourceRepoId));
    }

    private static boolean labelMatchesEndpointLabel(String label, ApiEndpoint ep) {
        if (label == null || ep == null) return false;
        int sp = label.indexOf(' ');
        if (sp < 0) return false;
        String method = label.substring(0, sp).trim();
        String path = label.substring(sp + 1).trim();
        return methodsMatch(ep.getHttpMethod(), method) && pathsMatchForPr(ep.getPath(), path);
    }

    private static boolean pathsMatchForPr(String dbPath, String extractedPath) {
        if (dbPath == null || extractedPath == null) return false;
        String a = normalizePath(dbPath);
        String b = normalizePath(extractedPath);
        if (a.equals(b)) return true;
        if (a.length() >= 8 && b.length() >= 8 && (a.endsWith(b) || b.endsWith(a))) return true;
        return false;
    }

    private static String normalizePath(String p) {
        return p.split("\\?")[0].replaceAll("/+$", "").toLowerCase(Locale.ROOT);
    }

    private static boolean methodsMatch(String dbMethod, String exMethod) {
        if (dbMethod == null || exMethod == null) return false;
        return dbMethod.equalsIgnoreCase(exMethod) || "REQUEST".equalsIgnoreCase(dbMethod);
    }

    // ── risk scoring ──────────────────────────────────────────────────────────

    /**
     * Score 0–10 based on:
     *   - affected repo count    (each repo = 2.0 pts, max 6.0)
     *   - affected file count    (each file = 0.3 pts, max 2.5)
     *   - changed endpoint count (each endpoint = 0.5 pts, max 1.5)
     */
    private RiskResult computeRisk(int repoCount, int fileCount, int endpointCount) {
        double score = Math.min(repoCount  * 2.0,  6.0)
                     + Math.min(fileCount  * 0.3,  2.5)
                     + Math.min(endpointCount * 0.5, 1.5);
        score = Math.round(score * 10.0) / 10.0; // 1 decimal place

        String label   = score >= 6.0 ? "HIGH" : score >= 3.0 ? "MEDIUM" : "LOW";
        String verdict = score >= 7.0 ? "BLOCKED"
                       : score >= 4.0 ? "REVIEW REQUIRED"
                       : "SAFE TO MERGE";
        return new RiskResult(score, label, verdict);
    }

    private record RiskResult(double score, String label, String verdict) {}
}
