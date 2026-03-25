package com.architect.service;

import com.architect.github.RateLimitExceededException;
import com.architect.model.*;
import com.architect.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepoScannerService {

    private final GitHubService gitHubService;
    private final EndpointExtractorService endpointExtractor;
    private final FrontendCallDetectorService frontendCallDetector;
    private final BackendHttpCallDetectorService backendHttpCallDetector;
    private final ImportTracerService importTracer;
    private final ConfigDependencyService configDependencyService;
    private final ScanProgressService scanProgressService;
    private final RepoRepository repoRepository;
    private final ApiEndpointRepository apiEndpointRepository;
    private final ApiCallRepository apiCallRepository;
    private final ComponentImportRepository componentImportRepository;
    private final ConfigDependencyRepository configDependencyRepository;
    private final DependencyEdgeRepository dependencyEdgeRepository;
    private final RuntimeWiringFactRepository runtimeWiringFactRepository;
    private final RuntimeWiringExtractorService runtimeWiringExtractorService;
    private final BackendOutboundHostExtractor backendOutboundHostExtractor;
    private final GitHubEtagCacheRepository githubEtagCacheRepository;
    private final com.architect.github.GitHubRateLimitMonitor gitHubRateLimitMonitor;
    private final TransactionTemplate transactionTemplate;

    public enum ScanMode { QUICK, DEEP, INCREMENTAL }

    private static final Set<String> SCANNABLE_EXTENSIONS = Set.of(
        ".java", ".js", ".ts", ".jsx", ".tsx", ".py", ".rb", ".go",
        ".vue", ".svelte", ".json", ".yaml", ".yml", ".xml", ".properties"
    );

    private static final Set<String> SKIP_DIRS = Set.of(
        "node_modules", ".git", "target", "build", "dist", "__pycache__",
        ".gradle", "vendor", ".idea", ".vscode", "coverage", ".nyc_output"
    );

    // Change 3 — high-value file patterns, scanned first
    private static final List<Pattern> PRIORITY_PATTERNS = List.of(
        Pattern.compile("(?i).*controller\\.\\w+$"),
        Pattern.compile("(?i).*router\\.\\w+$"),
        Pattern.compile("(?i).*routes\\.\\w+$"),
        Pattern.compile("(?i).*api\\.\\w+$"),
        Pattern.compile("(?i).*app\\.(js|ts|py|rb|go)$"),
        Pattern.compile("(?i).*server\\.(js|ts|py|rb|go)$"),
        Pattern.compile("(?i).*gateway\\.\\w+$"),
        Pattern.compile("(?i).*resource\\.\\w+$"),
        Pattern.compile("(?i).*handler\\.\\w+$"),
        Pattern.compile("(?i).*/index\\.(js|ts|py|rb|go)$")
    );

    /** If HEAD matches stored SHA, skip scanning (caller returns 200 before queueing async work). */
    public Optional<com.architect.dto.ScanStatusDto> checkIncrementalNoOp(Repo repo, String accessToken) {
        String[] p = repo.getFullName().split("/");
        String branch = repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main";
        String head = gitHubService.getBranchHeadSha(accessToken, p[0], p[1], branch);
        if (head != null && head.equals(repo.getLastScannedCommitSha())) {
            return Optional.of(com.architect.dto.ScanStatusDto.builder()
                    .repoId(repo.getId())
                    .repoName(repo.getName())
                    .status(Repo.ScanStatus.COMPLETE.name())
                    .message("No changes since last scan")
                    .build());
        }
        return Optional.empty();
    }

    /**
     * Invoked by {@link ScanJobAsyncExecutor} on {@code scanExecutor} after a task is claimed from the DB queue.
     */
    @CacheEvict(value = {"graph", "riskOverview", "insights"}, key = "#userId")
    public void scanRepoInternal(Long repoId, Long userId, ScanMode mode) {
        Repo repo = repoRepository.findByIdWithUser(repoId)
                .orElseThrow(() -> new IllegalArgumentException("Repo not found: " + repoId));
        if (!repo.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Repo does not belong to user");
        }
        performScan(repo, repo.getUser().getAccessToken(), mode);
    }

    /**
     * Not a single long {@code @Transactional}: one huge transaction made Hibernate/Postgres slow on large scans.
     * Clears are committed in a short transaction; per-entity saves use their own transactions.
     */
    private void performScan(Repo repo, String accessToken, ScanMode mode) {
        Instant scanStarted = Instant.now();
        log.info("Starting {} scan for repo: {}", mode, repo.getFullName());
        repo.setScanStatus(Repo.ScanStatus.SCANNING);
        repoRepository.save(repo);

        scanProgressService.emit(repo.getId(), "start",
            Map.of("repo", repo.getName(), "mode", mode.name()));

        try {
            transactionTemplate.executeWithoutResult(status -> clearPreviousScanData(repo));

            String[] parts = repo.getFullName().split("/");
            String owner = parts[0], repoName = parts[1];
            String branch = repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main";

            String headSha = gitHubService.getBranchHeadSha(accessToken, owner, repoName, branch);

            // Build set of known repo names for MONOREPO import classification
            List<Repo> userRepos = repoRepository.findByUser(repo.getUser());
            Set<String> knownRepoNames = new HashSet<>();
            for (Repo r : userRepos) {
                knownRepoNames.add(r.getName());
                knownRepoNames.add(r.getFullName());
            }

            List<Map<String, Object>> tree;
            if (mode == ScanMode.INCREMENTAL && repo.getLastScannedCommitSha() != null && !repo.getLastScannedCommitSha().isBlank()
                    && headSha != null) {
                List<Map<String, Object>> cmp = gitHubService.compareCommits(
                        accessToken, owner, repoName, repo.getLastScannedCommitSha(), headSha);
                tree = new ArrayList<>();
                for (Map<String, Object> f : cmp) {
                    String path = (String) f.get("filename");
                    if (path == null) continue;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("type", "blob");
                    item.put("path", path);
                    tree.add(item);
                }
                log.info("Incremental compare: {} changed paths for {}", tree.size(), repo.getFullName());
            } else {
                tree = gitHubService.getRepoTree(accessToken, repo, owner, repoName, branch);
            }

            // Change 3 — priority files (controllers, routers, api files) bubble to top
            List<Map<String, Object>> sorted = sortByPriority(tree);

            log.info("Repo {} has {} files (mode={})", repo.getFullName(), sorted.size(), mode);
            scanProgressService.emit(repo.getId(), "files_found",
                Map.of("total", sorted.size(), "mode", mode.name()));

            List<ApiEndpoint> endpoints = new ArrayList<>();
            int processed = 0;

            for (Map<String, Object> item : sorted) {
                String type = (String) item.get("type");
                String path = (String) item.get("path");
                if (!"blob".equals(type) || path == null) continue;
                if (shouldSkip(path)) continue;
                if (!isScannableFile(path)) continue;

                // Change 4 — QUICK mode: skip config/import-heavy files
                if (mode == ScanMode.QUICK && isDeepOnlyFile(path)) continue;

                gitHubRateLimitMonitor.maybeThrottleAfterRequest();
                String content = gitHubService.getFileContent(accessToken, repo, owner, repoName, path);
                if (content.isBlank()) continue;

                var extractedEndpoints = endpointExtractor.extract(content, path);
                for (var ep : extractedEndpoints) {
                    endpoints.add(apiEndpointRepository.save(ApiEndpoint.builder()
                        .repo(repo).path(ep.getPath()).httpMethod(ep.getHttpMethod())
                        .filePath(path).lineNumber(ep.getLineNumber())
                        .framework(ep.getFramework()).language(ep.getLanguage()).build()));
                }

                for (var call : frontendCallDetector.detect(content, path)) {
                    persistDetectedCall(repo, call.getUrlPattern(), call.getHttpMethod(),
                            call.getCallType(), path, call.getLineNumber());
                }
                // BackendHttpCallDetectorService now handles Java, Node.js (.js/.ts/.mjs),
                // and Python — run it for all those file types so backend-to-backend calls
                // (e.g. axios.get('http://catalog-service:3001/api/...') in a Node.js service)
                // are detected and matched against known endpoints.
                boolean isBackendFile = path.endsWith(".java")
                        || path.endsWith(".js") || path.endsWith(".ts")
                        || path.endsWith(".mjs") || path.endsWith(".cjs")
                        || path.endsWith(".py");
                if (isBackendFile) {
                    for (var call : backendHttpCallDetector.detect(content, path)) {
                        persistDetectedCall(repo, call.getUrlPattern(), call.getHttpMethod(),
                                call.getCallType(), path, call.getLineNumber());
                    }
                }
                if (path.endsWith(".java")) {
                    for (var oh : backendOutboundHostExtractor.extract(content, path)) {
                        runtimeWiringFactRepository.save(RuntimeWiringFact.builder()
                                .repo(repo)
                                .factType(RuntimeWiringFact.BACKEND_HOST)
                                .factKey(oh.getSourceKind())
                                .factValue(oh.getHost())
                                .sourceFile(path)
                                .lineNumber(oh.getLineNumber())
                                .build());
                    }
                }

                // DEEP / INCREMENTAL: imports + config refs
                if (mode == ScanMode.DEEP || mode == ScanMode.INCREMENTAL) {
                    for (var imp : importTracer.detect(content, path, repoName, knownRepoNames)) {
                        componentImportRepository.save(ComponentImport.builder()
                            .sourceRepo(repo).importPath(imp.getImportPath())
                            .componentName(imp.getComponentName())
                            .filePath(path).lineNumber(imp.getLineNumber())
                            .importType(imp.getImportType() != null ? imp.getImportType().name() : "EXTERNAL")
                            .resolvedFile(imp.getResolvedFile()).build());
                    }
                    for (var ref : configDependencyService.detect(content, path)) {
                        configDependencyRepository.save(ConfigDependency.builder()
                            .repo(repo).configFile(ref.getConfigFile())
                            .referencingFile(path).lineNumber(ref.getLineNumber()).build());
                    }
                    if (isRuntimeWiringScanPath(path)) {
                        for (var xf : runtimeWiringExtractorService.extract(content, path)) {
                            runtimeWiringFactRepository.save(RuntimeWiringFact.builder()
                                    .repo(repo)
                                    .factType(xf.getFactType())
                                    .factKey(xf.getFactKey())
                                    .factValue(xf.getFactValue())
                                    .sourceFile(path)
                                    .lineNumber(xf.getLineNumber())
                                    .build());
                        }
                        for (var oh : backendOutboundHostExtractor.extract(content, path)) {
                            runtimeWiringFactRepository.save(RuntimeWiringFact.builder()
                                    .repo(repo)
                                    .factType(RuntimeWiringFact.BACKEND_HOST)
                                    .factKey(oh.getSourceKind())
                                    .factValue(oh.getHost())
                                    .sourceFile(path)
                                    .lineNumber(oh.getLineNumber())
                                    .build());
                        }
                    }
                }

                processed++;

                // Change 2 — stream progress every 10 files
                if (processed % 10 == 0) {
                    scanProgressService.emit(repo.getId(), "progress", Map.of(
                        "filesProcessed", processed,
                        "endpointsFound", endpoints.size(),
                        "lastFile", path
                    ));
                }

                // Change 2 — instant emit when a priority file yields endpoints
                if (!extractedEndpoints.isEmpty() && isPriorityFile(path)) {
                    scanProgressService.emit(repo.getId(), "endpoint_found", Map.of(
                        "file", path,
                        "count", extractedEndpoints.size(),
                        "endpoints", extractedEndpoints.stream()
                            .map(e -> e.getHttpMethod() + " " + e.getPath()).toList()
                    ));
                }
            }

            ScanMode edgeMode = mode == ScanMode.INCREMENTAL ? ScanMode.DEEP : mode;
            int processedFiles = processed;
            // Keep a short transaction open for edge-building so Hibernate proxies (Repo, Endpoint)
            // can be safely initialized without holding a transaction for the entire scan.
            transactionTemplate.executeWithoutResult(tx -> {
                buildDependencyEdges(repo, edgeMode);
                logScanQualityMetrics(repo, processedFiles);
            });

            repo.setScanStatus(Repo.ScanStatus.COMPLETE);
            repo.setLastScannedAt(LocalDateTime.now());
            if (headSha != null) {
                repo.setLastScannedCommitSha(headSha);
            }
            repoRepository.save(repo);

            scanProgressService.emit(repo.getId(), "complete", Map.of(
                "endpointsFound", endpoints.size(),
                "filesProcessed", processed,
                "mode", mode.name()
            ));
            scanProgressService.complete(repo.getId());
            log.info("{} scan complete for {}: {} endpoints in {} ({} files)",
                mode, repo.getFullName(), endpoints.size(),
                Duration.between(scanStarted, Instant.now()), processed);

        } catch (RateLimitExceededException e) {
            log.warn("GitHub rate limit for {} — reset {}", repo.getFullName(), e.getResetAt());
            repo.setScanStatus(Repo.ScanStatus.RATE_LIMITED);
            repoRepository.save(repo);
            scanProgressService.emit(repo.getId(), "failed", Map.of("error", "github_rate_limit", "reset", String.valueOf(e.getResetAt())));
            scanProgressService.complete(repo.getId());
        } catch (Throwable e) {
            log.error("Scan failed for {}: {}", repo.getFullName(), e.getMessage(), e);
            repo.setScanStatus(Repo.ScanStatus.FAILED);
            repoRepository.save(repo);
            scanProgressService.emit(repo.getId(), "failed", Map.of("error", String.valueOf(e.getMessage())));
            scanProgressService.complete(repo.getId());
        }
    }

    // ── priority sort ─────────────────────────────────────────────────────────

    private List<Map<String, Object>> sortByPriority(List<Map<String, Object>> tree) {
        List<Map<String, Object>> high = new ArrayList<>(), normal = new ArrayList<>();
        for (Map<String, Object> item : tree) {
            String path = (String) item.get("path");
            (path != null && isPriorityFile(path) ? high : normal).add(item);
        }
        high.addAll(normal);
        return high;
    }

    private boolean isPriorityFile(String path) {
        return PRIORITY_PATTERNS.stream().anyMatch(p -> p.matcher(path).matches());
    }

    private boolean isDeepOnlyFile(String path) {
        return path.endsWith(".yaml") || path.endsWith(".yml")
            || path.endsWith(".xml")  || path.endsWith(".properties")
            || path.endsWith(".json");
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private void clearPreviousScanData(Repo repo) {
        githubEtagCacheRepository.deleteByRepo(repo);
        apiEndpointRepository.deleteByRepo(repo);
        apiCallRepository.deleteByCallerRepo(repo);
        componentImportRepository.deleteBySourceRepo(repo);
        configDependencyRepository.deleteByRepo(repo);
        dependencyEdgeRepository.deleteBySourceIdAndSourceType(repo.getId(), "REPO");
        runtimeWiringFactRepository.deleteByRepo(repo);
    }

    private void buildDependencyEdges(Repo repo, ScanMode mode) {
        List<Repo> allRepos = repoRepository.findByUser(repo.getUser());
        List<ApiEndpoint> allEndpoints = new ArrayList<>();
        for (Repo r : allRepos) allEndpoints.addAll(apiEndpointRepository.findByRepo(r));

        // Build PathSegmentIndex ONCE over all endpoints — O(N).
        // All subsequent call-matching uses the index: O(E_k) per call instead of O(N).
        PathSegmentIndex index = new PathSegmentIndex(allEndpoints);

        // Step 1 — match THIS repo's calls against all endpoints
        Set<String> seenCallEdges = new HashSet<>();
        for (ApiCall call : apiCallRepository.findByCallerRepo(repo)) {
            if (ApiCallUrlNormalizer.KIND_EXTERNAL.equals(call.getTargetKind())) {
                continue;
            }
            ApiEndpoint matched = matchCallToEndpoint(call, index);
            // Skip self-matches: a service should not be wired to its own endpoints.
            // Leave them UNRESOLVED so step 2 (retro-link) can correctly match them
            // once the target service is scanned and its endpoints are in the index.
            if (matched != null && matched.getRepo().getId().equals(repo.getId())) {
                matched = null;
            }
            if (matched != null) {
                call.setEndpoint(matched);
                call.setTargetKind(ApiCallUrlNormalizer.KIND_INTERNAL);
                apiCallRepository.save(call);
                if (seenCallEdges.add(repo.getId() + "->" + matched.getId())) {
                    dependencyEdgeRepository.save(DependencyEdge.builder()
                            .sourceId(repo.getId()).sourceType("REPO")
                            .targetId(matched.getId()).targetType("API_ENDPOINT")
                            .edgeType("CALLS").label("calls").build());
                }
            } else if (!ApiCallUrlNormalizer.KIND_EXTERNAL.equals(call.getTargetKind())) {
                call.setTargetKind(ApiCallUrlNormalizer.KIND_UNRESOLVED);
                apiCallRepository.save(call);
            }
        }

        // Step 2 — retroactively re-match ALL other repos' UNRESOLVED calls.
        // Fixes timing gap: Repo A was scanned before Repo B existed, so A's calls
        // to B's endpoints stayed UNRESOLVED. Reuse the same index (already built).
        List<ApiCall> otherUnresolved = apiCallRepository
                .findByCallerRepoUserIdAndEndpointIsNull(repo.getUser().getId());
        for (ApiCall call : otherUnresolved) {
            if (call.getCallerRepo().getId().equals(repo.getId())) continue;
            if (ApiCallUrlNormalizer.KIND_EXTERNAL.equals(call.getTargetKind())) continue;
            if (ApiCallUrlNormalizer.isExternalAbsoluteUrl(call.getUrlPattern())) {
                call.setTargetKind(ApiCallUrlNormalizer.KIND_EXTERNAL);
                apiCallRepository.save(call);
                continue;
            }
            ApiEndpoint matched = matchCallToEndpoint(call, index);
            // Exclude same-repo matches (caller's own endpoints) — leave as UNRESOLVED
            // so a later scan with the actual target repo present can link correctly.
            if (matched != null && !matched.getRepo().getId().equals(call.getCallerRepo().getId())) {
                call.setEndpoint(matched);
                call.setTargetKind(ApiCallUrlNormalizer.KIND_INTERNAL);
                apiCallRepository.save(call);
                log.debug("Retroactively linked callerRepo={} → endpoint id={} path={}",
                        call.getCallerRepo().getId(), matched.getId(), matched.getPath());
            }
        }

        if (mode == ScanMode.DEEP) {
            Set<String> seenImportEdges = new HashSet<>();
            for (ComponentImport imp : componentImportRepository.findBySourceRepo(repo)) {
                Repo targetRepo = matchRepo(imp.getImportPath(), allRepos);
                if (targetRepo != null && !targetRepo.getId().equals(repo.getId())) {
                    imp.setTargetRepo(targetRepo);
                    componentImportRepository.save(imp);
                    String edgeKey = repo.getId() + "->" + targetRepo.getId();
                    if (seenImportEdges.add(edgeKey)) {
                        dependencyEdgeRepository.save(DependencyEdge.builder()
                            .sourceId(repo.getId()).sourceType("REPO")
                            .targetId(targetRepo.getId()).targetType("REPO")
                            .edgeType("IMPORTS").label("imports")
                            .importType(imp.getImportType())
                            .sourceFile(imp.getFilePath())
                            .targetFile(imp.getResolvedFile())
                            .packagePath(imp.getImportPath()).build());
                    }
                }
            }
            // Also retroactively link OTHER repos' unmatched imports now that this repo is scanned
            for (Repo otherRepo : allRepos) {
                if (otherRepo.getId().equals(repo.getId())) continue;
                for (ComponentImport imp : componentImportRepository.findBySourceRepo(otherRepo)) {
                    if (imp.getTargetRepo() != null) continue;
                    Repo targetRepo = matchRepo(imp.getImportPath(), allRepos);
                    if (targetRepo != null && !targetRepo.getId().equals(otherRepo.getId())) {
                        imp.setTargetRepo(targetRepo);
                        componentImportRepository.save(imp);
                    }
                }
            }
        }
    }

    /**
     * Re-link all cross-repo connections for this user. Call this after all repos are connected
     * to ensure every repo's calls are matched against every other repo's endpoints, regardless
     * of scan order.
     */
    @Async("scanExecutor")
    @Transactional
    @CacheEvict(value = {"graph", "riskOverview"}, key = "#user.id")
    public void relinkAllRepos(User user) {
        log.info("Re-linking all cross-repo connections for user {}", user.getId());
        List<Repo> allRepos = repoRepository.findByUser(user);
        if (allRepos.isEmpty()) return;

        List<ApiEndpoint> allEndpoints = new ArrayList<>();
        for (Repo r : allRepos) allEndpoints.addAll(apiEndpointRepository.findByRepo(r));

        // Build index once — then match all unresolved calls in O(U × E_k)
        PathSegmentIndex index = new PathSegmentIndex(allEndpoints);
        List<ApiCall> unresolved = apiCallRepository.findByCallerRepoUserIdAndEndpointIsNull(user.getId());
        log.info("Re-linking {} unresolved calls across {} repos", unresolved.size(), allRepos.size());
        int linked = 0;
        for (ApiCall call : unresolved) {
            if (ApiCallUrlNormalizer.KIND_EXTERNAL.equals(call.getTargetKind())) continue;
            if (ApiCallUrlNormalizer.isExternalAbsoluteUrl(call.getUrlPattern())) {
                call.setTargetKind(ApiCallUrlNormalizer.KIND_EXTERNAL);
                apiCallRepository.save(call);
                continue;
            }
            ApiEndpoint matched = matchCallToEndpoint(call, index);
            if (matched != null && !matched.getRepo().getId().equals(call.getCallerRepo().getId())) {
                call.setEndpoint(matched);
                call.setTargetKind(ApiCallUrlNormalizer.KIND_INTERNAL);
                apiCallRepository.save(call);
                linked++;
            }
        }

        // Re-match every unlinked import
        for (Repo repo : allRepos) {
            for (ComponentImport imp : componentImportRepository.findBySourceRepo(repo)) {
                if (imp.getTargetRepo() != null) continue;
                Repo target = matchRepo(imp.getImportPath(), allRepos);
                if (target != null && !target.getId().equals(repo.getId())) {
                    imp.setTargetRepo(target);
                    componentImportRepository.save(imp);
                }
            }
        }
        log.info("Re-link complete: {} new cross-repo call links", linked);
    }

    private void persistDetectedCall(Repo repo, String urlPattern, String httpMethod,
                                     String callType, String filePath, int lineNumber) {
        if (urlPattern == null || urlPattern.isBlank()) return;
        String kind = ApiCallUrlNormalizer.isExternalAbsoluteUrl(urlPattern)
                ? ApiCallUrlNormalizer.KIND_EXTERNAL
                : ApiCallUrlNormalizer.KIND_UNRESOLVED;
        String host = ApiCallUrlNormalizer.KIND_EXTERNAL.equals(kind)
                ? ApiCallUrlNormalizer.extractExternalHost(urlPattern) : null;
        String norm = ApiCallUrlNormalizer.normalizeForMatching(urlPattern);
        apiCallRepository.save(ApiCall.builder()
                .callerRepo(repo).urlPattern(urlPattern).normalizedPattern(norm)
                .httpMethod(httpMethod).callType(callType)
                .filePath(filePath).lineNumber(lineNumber)
                .targetKind(kind).externalHost(host)
                .build());
    }

    /**
     * Match a call to its target endpoint using {@link PathSegmentIndex}.
     *
     * <p>Complexity: O(E_k) — only endpoints at the same path depth are compared.
     * Prefer HTTP-method match; fall back to first structural match if method unknown.
     */
    private ApiEndpoint matchCallToEndpoint(ApiCall call, PathSegmentIndex index) {
        if (ApiCallUrlNormalizer.isExternalAbsoluteUrl(call.getUrlPattern())) return null;
        String callPath = call.getNormalizedPattern();
        if (callPath == null || callPath.isBlank()) {
            callPath = ApiCallUrlNormalizer.normalizeForMatching(call.getUrlPattern());
        }
        List<ApiEndpoint> candidates = index.findCandidates(callPath);
        if (candidates.isEmpty()) {
            log.debug("Unresolved call: no endpoint candidates callerRepo={} method={} raw={} normalized={} file={}:{}",
                    call.getCallerRepo() != null ? call.getCallerRepo().getId() : "?",
                    call.getHttpMethod(), call.getUrlPattern(), callPath, call.getFilePath(), call.getLineNumber());
            return null;
        }
        String method = call.getHttpMethod();
        if (method != null && !method.isBlank()) {
            for (ApiEndpoint ep : candidates) {
                if (method.equalsIgnoreCase(ep.getHttpMethod())) return ep;
            }
            log.debug("Unresolved call: method mismatch callerRepo={} method={} normalized={} candidates={} sampleMethods={}",
                    call.getCallerRepo() != null ? call.getCallerRepo().getId() : "?",
                    method, callPath, candidates.size(),
                    candidates.stream().map(ApiEndpoint::getHttpMethod).distinct().limit(4).toList());
            return null;
        }
        return candidates.get(0);
    }

    private void logScanQualityMetrics(Repo repo, int processedFiles) {
        List<ApiCall> calls = apiCallRepository.findByCallerRepo(repo);
        int total = calls.size();
        int unresolved = 0;
        int internal = 0;
        for (ApiCall c : calls) {
            if (ApiCallUrlNormalizer.KIND_UNRESOLVED.equals(c.getTargetKind()) || c.getEndpoint() == null) {
                unresolved++;
            } else if (ApiCallUrlNormalizer.KIND_INTERNAL.equals(c.getTargetKind())) {
                internal++;
            }
        }
        int external = Math.max(0, total - unresolved - internal);
        double unresolvedPct = total == 0 ? 0.0 : (unresolved * 100.0 / total);
        double matchedPct = total == 0 ? 100.0 : (internal * 100.0 / total);

        List<Repo> userRepos = repoRepository.findByUser(repo.getUser());
        long complete = userRepos.stream().filter(r -> r.getScanStatus() == Repo.ScanStatus.COMPLETE).count();
        long totalRepos = userRepos.size();

        log.info("Scan quality repo={} filesProcessed={} callsTotal={} matchedInternal={} external={} unresolved={} unresolvedPct={} matchedPct={} reposScanned={}/{}",
                repo.getFullName(), processedFiles, total, internal, external, unresolved,
                String.format(Locale.ROOT, "%.1f", unresolvedPct),
                String.format(Locale.ROOT, "%.1f", matchedPct),
                complete, totalRepos);
    }

    private Repo matchRepo(String importPath, List<Repo> repos) {
        if (importPath == null || importPath.isBlank()) return null;
        String lower = importPath.toLowerCase().replace("_", "-");
        for (Repo r : repos) {
            String rn = r.getName().toLowerCase();
            if (lower.equals(rn) || lower.endsWith("/" + rn) || lower.contains(rn)) return r;
        }
        return null;
    }

    private boolean shouldSkip(String path) { return SKIP_DIRS.stream().anyMatch(path::contains); }
    private boolean isScannableFile(String path) { return SCANNABLE_EXTENSIONS.stream().anyMatch(path::endsWith); }

    /** application*.yml, bootstrap, vite.config, .env — runtime URL wiring (not Git) */
    private boolean isRuntimeWiringScanPath(String path) {
        String l = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (l.contains("vite.config") && (l.endsWith(".ts") || l.endsWith(".js") || l.endsWith(".mts"))) {
            return true;
        }
        if (l.endsWith(".env") || l.endsWith(".env.local") || l.endsWith(".env.development")) {
            return true;
        }
        if (l.endsWith("application.properties") || l.endsWith("bootstrap.properties")) {
            return true;
        }
        // Node.js server/gateway entry files for routing map detection
        String filename = l.contains("/") ? l.substring(l.lastIndexOf('/') + 1) : l;
        if (filename.equals("server.js") || filename.equals("server.ts") || filename.equals("server.mjs")
                || filename.equals("gateway.js") || filename.equals("gateway.ts")
                || filename.equals("index.js") || filename.equals("index.ts")
                || filename.equals("app.js") || filename.equals("app.ts")) {
            return true;
        }
        if (!(l.endsWith(".yml") || l.endsWith(".yaml"))) {
            return false;
        }
        return l.contains("application") || l.contains("bootstrap") || l.contains("gateway");
    }
}
