package com.architect.service;

import com.architect.dto.*;
import com.architect.model.*;
import com.architect.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GraphBuilderService {

    private final RepoRepository repoRepository;
    private final ApiEndpointRepository apiEndpointRepository;
    private final ApiCallRepository apiCallRepository;
    private final ComponentImportRepository componentImportRepository;
    private final RuntimeWiringFactRepository runtimeWiringFactRepository;
    private final RuntimeWiringGraphService runtimeWiringGraphService;

    @Cacheable(value = "graph", key = "#user.id")
    @Transactional(readOnly = true)
    public GraphDto buildGraph(User user) {
        List<Repo> repos = repoRepository.findByUserOrderByNameAsc(user);
        List<NodeDto> nodes = new ArrayList<>();
        List<EdgeDto> edges = new ArrayList<>();

        int totalEndpoints = 0;
        int totalCalls = 0;
        int totalImports = 0;
        int externalCalls = 0;

        List<RuntimeWiringFact> allFacts = runtimeWiringFactRepository.findAllByUserId(user.getId());
        Map<Long, List<RuntimeWiringFact>> factsByRepo = allFacts.stream()
                .collect(Collectors.groupingBy(f -> f.getRepo().getId()));

        for (Repo repo : repos) {
            long epCount = apiEndpointRepository.countByRepo(repo);
            totalEndpoints += (int) epCount;

            Map<String, Object> repoData = new LinkedHashMap<>();
            repoData.put("fullName", repo.getFullName());
            repoData.put("language", repo.getPrimaryLanguage() != null ? repo.getPrimaryLanguage() : "unknown");
            repoData.put("scanStatus", repo.getScanStatus().name());
            repoData.put("endpointCount", epCount);
            repoData.put("htmlUrl", repo.getHtmlUrl() != null ? repo.getHtmlUrl() : "");

            List<RuntimeWiringFact> fs = factsByRepo.getOrDefault(repo.getId(), List.of());
            for (RuntimeWiringFact fact : fs) {
                if (RuntimeWiringFact.APP_NAME.equals(fact.getFactType()) && fact.getFactValue() != null) {
                    repoData.putIfAbsent("springApplicationName", fact.getFactValue());
                }
            }
            List<String> regUrls = fs.stream()
                    .filter(f -> RuntimeWiringFact.EUREKA_REGISTRY.equals(f.getFactType()))
                    .map(RuntimeWiringFact::getFactValue)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!regUrls.isEmpty()) {
                repoData.put("registryUrls", regUrls);
            }
            if (fs.stream().anyMatch(f -> RuntimeWiringFact.GATEWAY_ROUTE.equals(f.getFactType()))) {
                repoData.put("isApiGateway", true);
            }
            List<Map<String, String>> viteProxies = new ArrayList<>();
            for (RuntimeWiringFact f : fs) {
                if (RuntimeWiringFact.VITE_PROXY.equals(f.getFactType())) {
                    viteProxies.add(Map.of(
                            "path", f.getFactKey() != null ? f.getFactKey() : "/api",
                            "target", f.getFactValue() != null ? f.getFactValue() : ""));
                }
            }
            if (!viteProxies.isEmpty()) {
                repoData.put("viteProxies", viteProxies);
            }
            List<String> outboundBe = fs.stream()
                    .filter(f -> RuntimeWiringFact.BACKEND_HOST.equals(f.getFactType()))
                    .map(RuntimeWiringFact::getFactValue)
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(24)
                    .toList();
            if (!outboundBe.isEmpty()) {
                repoData.put("outboundBackendHosts", outboundBe);
            }

            nodes.add(NodeDto.builder()
                    .id("repo-" + repo.getId())
                    .label(repo.getName())
                    .type("REPO")
                    .language(repo.getPrimaryLanguage())
                    .data(repoData)
                    .build());

            List<ApiEndpoint> endpoints = apiEndpointRepository.findByRepo(repo);
            for (ApiEndpoint ep : endpoints) {
                nodes.add(NodeDto.builder()
                        .id("ep-" + ep.getId())
                        .label(ep.getHttpMethod() + " " + ep.getPath())
                        .type("API_ENDPOINT")
                        .language(ep.getLanguage())
                        .data(Map.of(
                                "repoId", repo.getId(),
                                "repoName", repo.getName(),
                                "filePath", ep.getFilePath(),
                                "lineNumber", ep.getLineNumber() != null ? ep.getLineNumber() : 0,
                                "framework", ep.getFramework() != null ? ep.getFramework() : "unknown"
                        ))
                        .build());

                edges.add(EdgeDto.builder()
                        .id("edge-repo-ep-" + ep.getId())
                        .source("repo-" + repo.getId())
                        .target("ep-" + ep.getId())
                        .label("defines")
                        .type("DEFINES")
                        .build());
            }

            List<ApiCall> calls = apiCallRepository.findByCallerRepo(repo);
            totalCalls += calls.size();
            for (ApiCall c : calls) {
                if (ApiCallUrlNormalizer.KIND_EXTERNAL.equals(c.getTargetKind())) {
                    externalCalls++;
                }
            }
        }

        // Cross-repo CALLS: aggregated repo → repo (product core)
        Map<String, LinkedHashSet<String>> crossRepoEndpoints = new LinkedHashMap<>();
        for (Repo r : repos) {
            for (ApiCall c : apiCallRepository.findByCallerRepo(r)) {
                if (c.getEndpoint() == null) continue;
                Repo targetRepo = c.getEndpoint().getRepo();
                if (targetRepo.getId().equals(r.getId())) continue;
                String k = r.getId() + "->" + targetRepo.getId();
                crossRepoEndpoints.computeIfAbsent(k, x -> new LinkedHashSet<>())
                        .add(c.getEndpoint().getHttpMethod() + " " + c.getEndpoint().getPath());
            }
        }
        Map<Long, String> repoNameById = new HashMap<>();
        for (Repo r : repos) {
            repoNameById.put(r.getId(), r.getName());
        }

        for (Map.Entry<String, LinkedHashSet<String>> e : crossRepoEndpoints.entrySet()) {
            String[] parts = e.getKey().split("->");
            long from = Long.parseLong(parts[0]);
            long to = Long.parseLong(parts[1]);
            List<String> epList = new ArrayList<>(e.getValue());
            String fromName = repoNameById.getOrDefault(from, "repo-" + from);
            String toName = repoNameById.getOrDefault(to, "repo-" + to);
            Map<String, Object> callData = new LinkedHashMap<>();
            callData.put("callTier", "REPO_TO_REPO");
            callData.put("fromRepoName", fromName);
            callData.put("toRepoName", toName);
            callData.put("fromRepoId", from);
            callData.put("toRepoId", to);
            callData.put("endpoints", epList);
            edges.add(EdgeDto.builder()
                    .id("call-cross-" + from + "-" + to)
                    .source("repo-" + from)
                    .target("repo-" + to)
                    .label(fromName + " → " + toName)
                    .type("CALLS")
                    .data(callData)
                    .build());
        }

        // Same-repo CALLS: repo → endpoint (intra-service)
        for (Repo r : repos) {
            Set<String> seen = new HashSet<>();
            for (ApiCall c : apiCallRepository.findByCallerRepo(r)) {
                if (c.getEndpoint() == null) continue;
                if (!c.getEndpoint().getRepo().getId().equals(r.getId())) continue;
                String ek = "call-intra-" + c.getId();
                if (seen.add(ek)) {
                    edges.add(EdgeDto.builder()
                            .id(ek)
                            .source("repo-" + r.getId())
                            .target("ep-" + c.getEndpoint().getId())
                            .label("calls")
                            .type("CALLS")
                            .data(Map.of(
                                    "callTier", "REPO_TO_ENDPOINT",
                                    "urlPattern", c.getUrlPattern() != null ? c.getUrlPattern() : ""))
                            .build());
                }
            }
        }

        for (Repo repo : repos) {
            List<ComponentImport> imports = componentImportRepository.findBySourceRepo(repo);
            totalImports += imports.size();
            for (ComponentImport imp : imports) {
                if (imp.getTargetRepo() != null) {
                    String importType = imp.getImportType() != null ? imp.getImportType() : "EXTERNAL";
                    Map<String, Object> edgeData = new HashMap<>();
                    edgeData.put("importType", importType);
                    edgeData.put("sourceFile", imp.getFilePath() != null ? imp.getFilePath() : "");
                    edgeData.put("fromRepoName", repo.getName());
                    edgeData.put("toRepoName", imp.getTargetRepo().getName());
                    if (imp.getResolvedFile() != null) edgeData.put("resolvedFile", imp.getResolvedFile());
                    edges.add(EdgeDto.builder()
                            .id("edge-import-" + imp.getId())
                            .source("repo-" + repo.getId())
                            .target("repo-" + imp.getTargetRepo().getId())
                            .label(repo.getName() + " → " + imp.getTargetRepo().getName())
                            .type("IMPORTS")
                            .data(edgeData)
                            .build());
                }
            }
        }

        List<EdgeDto> wiredEdges = runtimeWiringGraphService.buildWiredEdges(user, repos);
        edges.addAll(wiredEdges);

        Set<String> seenEdges = new HashSet<>();
        List<EdgeDto> uniqueEdges = new ArrayList<>();
        for (EdgeDto e : edges) {
            String key = e.getSource() + "->" + e.getTarget() + "-" + e.getType() + "-" + e.getId();
            if (seenEdges.add(key)) uniqueEdges.add(e);
        }

        int finalExternal = externalCalls;
        int wiredCount = (int) uniqueEdges.stream().filter(e -> "WIRED".equals(e.getType())).count();
        return GraphDto.builder()
                .nodes(nodes)
                .edges(uniqueEdges)
                .stats(GraphDto.GraphStats.builder()
                        .totalRepos(repos.size())
                        .totalEndpoints(totalEndpoints)
                        .totalCalls(totalCalls)
                        .totalImports(totalImports)
                        .totalEdges(uniqueEdges.size())
                        .totalExternalCalls(finalExternal)
                        .totalWiredEdges(wiredCount)
                        .build())
                .build();
    }
}
