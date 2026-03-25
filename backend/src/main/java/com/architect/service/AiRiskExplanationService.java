package com.architect.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.architect.config.AppProperties;
import com.architect.dto.AiRiskExplanation;
import com.architect.dto.AiRiskInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * One-shot Anthropic call to turn {@link AiRiskInput} into a structured explanation.
 * Failures return {@code null}; core PR verdicts and scores are never modified here.
 */
@Service
public class AiRiskExplanationService {

    private static final Logger log = LoggerFactory.getLogger(AiRiskExplanationService.class);

    private static final String SYSTEM_PROMPT = """
            You are a senior software engineer reviewing a pull request in a microservices architecture.

            Your job is to:
            1. Explain the real-world impact of the change
            2. Highlight what could break
            3. Suggest practical next steps

            Rules:
            - Be concise (max 5 bullet points)
            - No fluff or generic advice
            - Use specific repo/file names if provided
            - Do NOT hallucinate unknown systems
            - If confidence is low, explicitly mention uncertainty
            - Focus on developer decision-making

            Output must be valid JSON with keys: summary, impact, recommendations, confidenceNote.""";

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final Cache<String, AiRiskExplanation> prExplanationCache;

    private volatile AnthropicClient anthropicClient;

    public AiRiskExplanationService(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.prExplanationCache = Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(4, TimeUnit.HOURS)
                .build();
    }

    @PostConstruct
    void initAnthropic() {
        String apiKey = appProperties.getAnthropic().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ANTHROPIC_API_KEY not set — PR AI insight disabled");
            return;
        }
        long timeoutMs = Math.max(500L, appProperties.getAnthropic().getPrRiskExplanationTimeoutMs());
        this.anthropicClient = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        log.info("Anthropic client initialized for PR risk explanations (enforced timeout {} ms via Future)", timeoutMs);
    }

    /**
     * Returns a structured explanation, or {@code null} if skipped (LOW risk), disabled, timed out, or invalid.
     */
    public AiRiskExplanation explainRisk(AiRiskInput input) {
        if (input == null || "LOW".equalsIgnoreCase(input.riskLevel())) {
            return null;
        }
        if (anthropicClient == null) {
            return null;
        }
        return invokeLlm(input);
    }

    /**
     * Cached per PR head; still returns {@code null} on miss/failure.
     */
    public AiRiskExplanation explainRiskForPullRequest(
            String owner,
            String repo,
            int prNumber,
            String headSha,
            AiRiskInput input) {
        if (input == null || "LOW".equalsIgnoreCase(input.riskLevel())) {
            return null;
        }
        String key = owner + "/" + repo + "#" + prNumber + "@" + Objects.toString(headSha, "");
        AiRiskExplanation cached = prExplanationCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        AiRiskExplanation fresh = explainRisk(input);
        if (fresh != null) {
            prExplanationCache.put(key, fresh);
        }
        return fresh;
    }

    private AiRiskExplanation invokeLlm(AiRiskInput input) {
        final String userJson;
        try {
            userJson = objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            log.debug("ai_risk_explanation skip: could not serialize input", e);
            return null;
        }

        long timeoutMs = Math.max(500L, appProperties.getAnthropic().getPrRiskExplanationTimeoutMs());
        long maxTokens = Math.max(64L, appProperties.getAnthropic().getPrRiskExplanationMaxTokens());
        double temperature = appProperties.getAnthropic().getPrRiskExplanationTemperature();
        if (temperature < 0 || temperature > 1) {
            temperature = 0.25;
        }

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_OPUS_4_6)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .system(SYSTEM_PROMPT)
                .addUserMessage(userJson)
                .build();

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ai-pr-risk-explanation");
            t.setDaemon(true);
            return t;
        });
        Future<String> future = executor.submit(() -> collectStreamingResponse(params));
        try {
            String raw = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return parseExplanation(raw);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.debug("ai_risk_explanation timeout after {} ms", timeoutMs);
            return null;
        } catch (Exception e) {
            future.cancel(true);
            log.debug("ai_risk_explanation failed: {}", e.getMessage());
            return null;
        } finally {
            executor.shutdownNow();
        }
    }

    private String collectStreamingResponse(MessageCreateParams params) throws Exception {
        StringBuilder buffer = new StringBuilder();
        try (StreamResponse<RawMessageStreamEvent> stream = anthropicClient.messages().createStreaming(params)) {
            stream.stream()
                    .flatMap(event -> event.contentBlockDelta().stream())
                    .flatMap(delta -> delta.delta().text().stream())
                    .forEach(textDelta -> buffer.append(textDelta.text()));
        }
        return buffer.toString().trim();
    }

    private AiRiskExplanation parseExplanation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String json = unwrapJsonFence(raw);
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.hasNonNull("summary") || root.get("summary").asText("").isBlank()) {
                return null;
            }
            String summary = root.get("summary").asText().trim();
            List<String> impact = readStringList(root, "impact");
            List<String> recommendations = readStringList(root, "recommendations");
            String confidenceNote = root.hasNonNull("confidenceNote")
                    ? root.get("confidenceNote").asText().trim()
                    : null;
            if (confidenceNote != null && confidenceNote.isEmpty()) {
                confidenceNote = null;
            }
            return new AiRiskExplanation(summary, impact, recommendations, confidenceNote);
        } catch (Exception e) {
            log.debug("ai_risk_explanation invalid JSON", e);
            return null;
        }
    }

    private static String unwrapJsonFence(String raw) {
        String t = raw.trim();
        if (!t.startsWith("```")) {
            return t;
        }
        int firstNl = t.indexOf('\n');
        if (firstNl < 0) {
            return t;
        }
        t = t.substring(firstNl + 1).trim();
        if (t.endsWith("```")) {
            t = t.substring(0, t.length() - 3).trim();
        }
        return t;
    }

    private static List<String> readStringList(JsonNode root, String field) {
        if (!root.has(field) || root.get(field).isNull()) {
            return List.of();
        }
        JsonNode n = root.get(field);
        if (n.isTextual()) {
            String s = n.asText().trim();
            return s.isEmpty() ? List.of() : List.of(s);
        }
        if (!n.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        n.forEach(item -> {
            if (item != null && !item.isNull()) {
                String s = item.asText("").trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        });
        return List.copyOf(out);
    }
}
