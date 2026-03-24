package com.architect.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.*;
import com.architect.config.AppProperties;
import com.architect.model.*;
import com.architect.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AppProperties appProperties;
    private final RepoRepository repoRepository;
    private final ApiEndpointRepository apiEndpointRepository;
    private final ApiCallRepository apiCallRepository;
    private final ComponentImportRepository componentImportRepository;
    private final AiQueryHistoryRepository aiQueryHistoryRepository;
    private final UserRepository userRepository;

    private AnthropicClient anthropicClient;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @PostConstruct
    public void init() {
        String apiKey = appProperties.getAnthropic().getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            anthropicClient = AnthropicOkHttpClient.builder()
                    .apiKey(apiKey)
                    .build();
            log.info("Anthropic client initialized");
        } else {
            log.warn("ANTHROPIC_API_KEY not set — AI features will return mock responses");
        }
    }

    // ── Natural Language Query ─────────────────────────────────────────────
    public SseEmitter nlQuery(String question, Long userId) {
        SseEmitter emitter = new SseEmitter(120_000L);
        executor.submit(() -> {
            try {
                String context = buildGraphContext(userId);
                String prompt = buildNlQueryPrompt(question, context);
                StringBuilder fullResponse = new StringBuilder();

                if (anthropicClient != null) {
                    streamResponse(prompt, emitter, fullResponse);
                } else {
                    String mock = mockNlQueryResponse(question);
                    sendSseChunks(emitter, mock, fullResponse);
                }

                saveQueryHistory(userId, question, fullResponse.toString(), "NL_QUERY");
                emitter.complete();
            } catch (Exception e) {
                log.error("NL query failed", e);
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            }
        });
        return emitter;
    }

    // ── Codebase Q&A Chat ──────────────────────────────────────────────────
    public SseEmitter chat(String message, Long userId) {
        SseEmitter emitter = new SseEmitter(120_000L);
        executor.submit(() -> {
            try {
                String context = buildGraphContext(userId);
                String prompt = buildChatPrompt(message, context);
                StringBuilder fullResponse = new StringBuilder();

                if (anthropicClient != null) {
                    streamResponse(prompt, emitter, fullResponse);
                } else {
                    sendSseChunks(emitter, "I can answer questions about your architecture. " +
                            "Currently in demo mode — connect an ANTHROPIC_API_KEY for full AI responses.", fullResponse);
                }

                saveQueryHistory(userId, message, fullResponse.toString(), "CHAT");
                emitter.complete();
            } catch (Exception e) {
                log.error("Chat failed", e);
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            }
        });
        return emitter;
    }

    // ── Architecture Docs Generation ───────────────────────────────────────
    public SseEmitter generateArchDocs(Long repoId, Long userId) {
        SseEmitter emitter = new SseEmitter(180_000L);
        executor.submit(() -> {
            try {
                Repo repo = repoRepository.findById(repoId)
                        .orElseThrow(() -> new RuntimeException("Repo not found"));
                String repoContext = buildRepoContext(repo);
                String prompt = buildArchDocsPrompt(repo, repoContext);
                StringBuilder fullResponse = new StringBuilder();

                if (anthropicClient != null) {
                    streamResponse(prompt, emitter, fullResponse);
                } else {
                    sendSseChunks(emitter, generateMockArchDocs(repo), fullResponse);
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("Arch docs generation failed", e);
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            }
        });
        return emitter;
    }

    // ── Developer Onboarding Mode ──────────────────────────────────────────
    public SseEmitter onboardingTrace(String feature, Long userId) {
        SseEmitter emitter = new SseEmitter(180_000L);
        executor.submit(() -> {
            try {
                String context = buildGraphContext(userId);
                String prompt = buildOnboardingPrompt(feature, context);
                StringBuilder fullResponse = new StringBuilder();

                if (anthropicClient != null) {
                    streamResponse(prompt, emitter, fullResponse);
                } else {
                    sendSseChunks(emitter, generateMockOnboarding(feature), fullResponse);
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("Onboarding trace failed", e);
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            }
        });
        return emitter;
    }

    // ── Anomaly Detection ─────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Map<String, Object>> detectAnomalies(Long userId) {
        List<Map<String, Object>> anomalies = new ArrayList<>();

        List<Repo> repos = repoRepository.findByUserId(userId);
        if (repos.isEmpty()) return anomalies;

        // 1. Detect calls to non-existent endpoints
        List<ApiCall> allCalls = apiCallRepository.findByCallerRepoUserIdAndEndpointIsNull(userId);
        for (ApiCall call : allCalls) {
            Map<String, Object> anomaly = new HashMap<>();
            anomaly.put("type", "BROKEN_API_CALL");
            anomaly.put("severity", "HIGH");
            anomaly.put("description", "Call to '" + call.getUrlPattern() + "' has no matching endpoint in any scanned repo");
            anomaly.put("repo", call.getCallerRepo().getName());
            anomaly.put("file", call.getFilePath());
            anomaly.put("line", call.getLineNumber());
            anomalies.add(anomaly);
        }

        // 2. Detect orphan endpoints (no callers)
        for (Repo repo : repos) {
            List<ApiEndpoint> endpoints = apiEndpointRepository.findByRepoId(repo.getId());
            for (ApiEndpoint ep : endpoints) {
                long callerCount = apiCallRepository.countByEndpointId(ep.getId());
                if (callerCount == 0) {
                    Map<String, Object> anomaly = new HashMap<>();
                    anomaly.put("type", "ORPHAN_ENDPOINT");
                    anomaly.put("severity", "MEDIUM");
                    anomaly.put("description", "Endpoint " + ep.getHttpMethod() + " " + ep.getPath() + " has no known callers");
                    anomaly.put("repo", repo.getName());
                    anomaly.put("file", ep.getFilePath());
                    anomaly.put("line", ep.getLineNumber());
                    anomalies.add(anomaly);
                }
            }
        }

        // 3. Detect circular dependencies
        List<Map<String, Object>> circular = detectCircularDependencies(repos, userId);
        anomalies.addAll(circular);

        return anomalies;
    }

    // ── Tech Debt Radar ───────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Map<String, Object> getTechDebt(Long userId) {
        Map<String, Object> report = new LinkedHashMap<>();
        List<Repo> repos = repoRepository.findByUserId(userId);

        int orphanEndpoints = 0;
        int brokenCalls = 0;
        List<String> circularDeps = new ArrayList<>();
        Map<String, Integer> repoDebtScore = new LinkedHashMap<>();

        for (Repo repo : repos) {
            int score = 0;
            List<ApiEndpoint> eps = apiEndpointRepository.findByRepoId(repo.getId());
            for (ApiEndpoint ep : eps) {
                if (apiCallRepository.countByEndpointId(ep.getId()) == 0) {
                    orphanEndpoints++;
                    score += 5;
                }
            }
            brokenCalls += (int) apiCallRepository.countByCallerRepoIdAndEndpointIsNull(repo.getId());
            score += (int) apiCallRepository.countByCallerRepoIdAndEndpointIsNull(repo.getId()) * 10;
            repoDebtScore.put(repo.getName(), score);
        }

        report.put("totalRepos", repos.size());
        report.put("orphanEndpoints", orphanEndpoints);
        report.put("brokenCalls", brokenCalls);
        report.put("circularDependencies", circularDeps);
        report.put("repoDebtScores", repoDebtScore);
        report.put("overallRisk", calculateOverallRisk(orphanEndpoints, brokenCalls));
        report.put("recommendations", buildRecommendations(orphanEndpoints, brokenCalls, circularDeps));

        return report;
    }

    /**
     * Phase 4: one-shot narrative (not chat) — why a PR was flagged.
     */
    public String explainPrRisk(Long userId, String verdict, double numericScore, String scenario,
                                java.util.List<String> riskFactors) {
        String factors = riskFactors == null || riskFactors.isEmpty()
            ? "No extra factors."
            : String.join(" ", riskFactors).replace("**", "");
        String prompt = "You are an architect bot. In exactly 2-4 short sentences (plain English, no bullets), "
            + "explain to an engineering lead WHY this PR matters. "
            + "Verdict: " + verdict + ". Risk score: " + String.format("%.1f", numericScore) + "/10. "
            + "Scenario: " + scenario + ". Context: " + factors
            + "\n\nDo not suggest opening a chat. End with one concrete action.";

        if (anthropicClient == null) {
            return "**Demo mode** — set `ANTHROPIC_API_KEY` for AI narrative.\n\n"
                + "Summary: " + verdict + " (" + String.format("%.1f", numericScore) + "/10). "
                + (riskFactors != null && !riskFactors.isEmpty() ? riskFactors.get(0).replace("**", "") : "");
        }
        StringBuilder buf = new StringBuilder();
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(Model.CLAUDE_OPUS_4_6)
                    .maxTokens(400L)
                    .addUserMessage(prompt)
                    .build();
            try (StreamResponse<RawMessageStreamEvent> stream = anthropicClient.messages().createStreaming(params)) {
                stream.stream()
                        .flatMap(event -> event.contentBlockDelta().stream())
                        .flatMap(delta -> delta.delta().text().stream())
                        .forEach(textDelta -> buf.append(textDelta.text()));
            }
            return buf.toString().trim();
        } catch (Exception e) {
            log.warn("explainPrRisk failed", e);
            return factors.length() > 200 ? factors.substring(0, 200) + "…" : factors;
        }
    }

    // ── Query History ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getQueryHistory(Long userId) {
        return aiQueryHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(20)
                .map(q -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", q.getId());
                    m.put("query", q.getQueryText());
                    m.put("type", q.getQueryType());
                    m.put("createdAt", q.getCreatedAt());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void streamResponse(String prompt, SseEmitter emitter, StringBuilder buffer) throws Exception {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_OPUS_4_6)
                .maxTokens(4096L)
                .addUserMessage(prompt)
                .build();

        try (StreamResponse<RawMessageStreamEvent> stream = anthropicClient.messages().createStreaming(params)) {
            stream.stream()
                    .flatMap(event -> event.contentBlockDelta().stream())
                    .flatMap(delta -> delta.delta().text().stream())
                    .forEach(textDelta -> {
                        String text = textDelta.text();
                        buffer.append(text);
                        try {
                            emitter.send(SseEmitter.event().data(text));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private void sendSseChunks(SseEmitter emitter, String text, StringBuilder buffer) throws IOException {
        buffer.append(text);
        // Send in chunks to simulate streaming
        String[] words = text.split(" ");
        for (String word : words) {
            emitter.send(SseEmitter.event().data(word + " "));
            try { Thread.sleep(20); } catch (InterruptedException ignored) {}
        }
    }

    private String buildGraphContext(Long userId) {
        List<Repo> repos = repoRepository.findByUserId(userId);
        StringBuilder ctx = new StringBuilder();
        ctx.append("=== CODEBASE ARCHITECTURE CONTEXT ===\n\n");
        ctx.append("Repos (").append(repos.size()).append(" total):\n");
        for (Repo r : repos) {
            ctx.append("- ").append(r.getFullName())
               .append(" [").append(r.getPrimaryLanguage()).append("]")
               .append(" status=").append(r.getScanStatus()).append("\n");
            List<ApiEndpoint> eps = apiEndpointRepository.findByRepoId(r.getId());
            for (ApiEndpoint ep : eps.stream().limit(10).toList()) {
                ctx.append("  endpoint: ").append(ep.getHttpMethod()).append(" ").append(ep.getPath())
                   .append(" (").append(ep.getFramework()).append(")\n");
            }
            if (eps.size() > 10) ctx.append("  ... and ").append(eps.size() - 10).append(" more endpoints\n");
        }

        // Add cross-repo calls
        List<ApiCall> calls = apiCallRepository.findByCallerRepoUserIdWithEndpoint(userId);
        if (!calls.isEmpty()) {
            ctx.append("\nCross-repo API calls:\n");
            for (ApiCall call : calls.stream().limit(20).toList()) {
                if (call.getEndpoint() != null) {
                    ctx.append("- ").append(call.getCallerRepo().getName())
                       .append(" -> ").append(call.getEndpoint().getRepo().getName())
                       .append(" (").append(call.getUrlPattern()).append(")\n");
                }
            }
        }

        // Add component imports
        List<ComponentImport> imports = componentImportRepository.findBySourceRepoUserId(userId);
        if (!imports.isEmpty()) {
            ctx.append("\nCross-repo imports:\n");
            for (ComponentImport imp : imports.stream().limit(20).toList()) {
                if (imp.getTargetRepo() != null) {
                    ctx.append("- ").append(imp.getSourceRepo().getName())
                       .append(" imports ").append(imp.getComponentName())
                       .append(" from ").append(imp.getTargetRepo().getName()).append("\n");
                }
            }
        }
        return ctx.toString();
    }

    private String buildRepoContext(Repo repo) {
        StringBuilder ctx = new StringBuilder();
        ctx.append("Repo: ").append(repo.getFullName()).append("\n");
        ctx.append("Language: ").append(repo.getPrimaryLanguage()).append("\n");
        ctx.append("Description: ").append(repo.getDescription()).append("\n\n");

        List<ApiEndpoint> eps = apiEndpointRepository.findByRepoId(repo.getId());
        ctx.append("Exposed API endpoints (").append(eps.size()).append("):\n");
        for (ApiEndpoint ep : eps) {
            ctx.append("  ").append(ep.getHttpMethod()).append(" ").append(ep.getPath())
               .append(" @ ").append(ep.getFilePath()).append(":").append(ep.getLineNumber())
               .append(" [").append(ep.getFramework()).append("]\n");
        }

        List<ApiCall> outgoing = apiCallRepository.findByCallerRepoId(repo.getId());
        ctx.append("\nOutgoing API calls (").append(outgoing.size()).append("):\n");
        for (ApiCall call : outgoing) {
            ctx.append("  -> ").append(call.getUrlPattern())
               .append(" @ ").append(call.getFilePath()).append("\n");
        }

        return ctx.toString();
    }

    private String buildNlQueryPrompt(String question, String context) {
        return context + "\n\n=== USER QUESTION ===\n" + question +
               "\n\nAnswer concisely based on the architecture context above. " +
               "If referencing specific repos or endpoints, be precise.";
    }

    private String buildChatPrompt(String message, String context) {
        return "You are an expert software architect assistant with knowledge of this specific codebase.\n\n"
               + context + "\n\n=== DEVELOPER QUESTION ===\n" + message +
               "\n\nProvide a helpful, accurate answer about the architecture.";
    }

    private String buildArchDocsPrompt(Repo repo, String repoContext) {
        return "Generate comprehensive architecture documentation for this repository in Markdown format.\n\n"
               + repoContext +
               "\n\nInclude:\n" +
               "1. ## Overview — what this service does\n" +
               "2. ## API Endpoints — list all endpoints with descriptions\n" +
               "3. ## Dependencies — what this service calls\n" +
               "4. ## Architecture Notes — key patterns observed\n" +
               "5. ## Getting Started — quick start for a new developer\n\n" +
               "Be specific to the actual endpoints and patterns seen in the context.";
    }

    private String buildOnboardingPrompt(String feature, String context) {
        return "You are an expert developer onboarding assistant.\n\n" + context +
               "\n\n=== NEW DEVELOPER QUESTION ===\n" +
               "How does the '" + feature + "' flow work end-to-end in this system?\n\n" +
               "Trace the complete path: which repos are involved, which APIs are called, " +
               "which components import from which. Be specific with actual repo and endpoint names from the context. " +
               "Format as a numbered step-by-step trace.";
    }

    private List<Map<String, Object>> detectCircularDependencies(List<Repo> repos, Long userId) {
        List<Map<String, Object>> circular = new ArrayList<>();
        // Build adjacency map
        Map<Long, Set<Long>> adj = new HashMap<>();
        for (Repo r : repos) adj.put(r.getId(), new HashSet<>());

        List<ComponentImport> imports = componentImportRepository.findBySourceRepoUserId(userId);
        for (ComponentImport imp : imports) {
            if (imp.getTargetRepo() != null) {
                adj.computeIfAbsent(imp.getSourceRepo().getId(), k -> new HashSet<>())
                   .add(imp.getTargetRepo().getId());
            }
        }

        // Simple cycle detection for pairs
        for (Repo a : repos) {
            for (Repo b : repos) {
                if (!a.getId().equals(b.getId())) {
                    Set<Long> aNeighbors = adj.getOrDefault(a.getId(), Set.of());
                    Set<Long> bNeighbors = adj.getOrDefault(b.getId(), Set.of());
                    if (aNeighbors.contains(b.getId()) && bNeighbors.contains(a.getId())) {
                        Map<String, Object> anomaly = new HashMap<>();
                        anomaly.put("type", "CIRCULAR_DEPENDENCY");
                        anomaly.put("severity", "HIGH");
                        anomaly.put("description", "Circular dependency detected: " + a.getName() + " <-> " + b.getName());
                        anomaly.put("repo", a.getName());
                        circular.add(anomaly);
                    }
                }
            }
        }
        return circular;
    }

    private String calculateOverallRisk(int orphans, int broken) {
        int score = orphans * 2 + broken * 10;
        if (score > 50) return "HIGH";
        if (score > 15) return "MEDIUM";
        return "LOW";
    }

    private List<String> buildRecommendations(int orphans, int broken, List<String> circular) {
        List<String> recs = new ArrayList<>();
        if (broken > 0) recs.add("Fix " + broken + " broken API call(s) — calls referencing endpoints not found in scanned repos");
        if (orphans > 5) recs.add("Review " + orphans + " orphan endpoints — consider removing if truly unused");
        if (!circular.isEmpty()) recs.add("Resolve " + circular.size() + " circular dependency cycle(s) — these create tight coupling");
        if (recs.isEmpty()) recs.add("Architecture looks healthy — no critical issues detected");
        return recs;
    }

    private void saveQueryHistory(Long userId, String query, String response, String type) {
        try {
            userRepository.findById(userId).ifPresent(user -> {
                AiQueryHistory h = new AiQueryHistory();
                h.setUser(user);
                h.setQueryText(query);
                h.setResponseText(response);
                h.setQueryType(type);
                aiQueryHistoryRepository.save(h);
            });
        } catch (Exception e) {
            log.warn("Failed to save query history", e);
        }
    }

    // Mock responses for when API key is not configured
    private String mockNlQueryResponse(String question) {
        return "**Demo Mode** — Connect an `ANTHROPIC_API_KEY` for real AI responses.\n\n" +
               "Your question: *" + question + "*\n\n" +
               "Based on the scanned architecture, I would analyze the dependency graph to find " +
               "relevant repos, endpoints, and call patterns that relate to your question.";
    }

    private String generateMockArchDocs(Repo repo) {
        return "# " + repo.getName() + " — Architecture Overview\n\n" +
               "> **Demo Mode** — Connect an `ANTHROPIC_API_KEY` for AI-generated docs.\n\n" +
               "## Overview\nThis service is part of your scanned organization.\n\n" +
               "## API Endpoints\nSee the dependency graph for all discovered endpoints.\n\n" +
               "## Getting Started\nScan the repository to discover all endpoints and dependencies.";
    }

    private String generateMockOnboarding(String feature) {
        return "# How '" + feature + "' Works\n\n" +
               "> **Demo Mode** — Connect an `ANTHROPIC_API_KEY` for AI-generated onboarding traces.\n\n" +
               "## Flow Overview\nThis onboarding trace would walk you through the end-to-end " +
               "path for `" + feature + "`, showing all repos, APIs, and components involved.";
    }
}
