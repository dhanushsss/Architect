package com.architect.controller;

import com.architect.security.JwtTokenProvider;
import com.architect.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping(value = "/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter nlQuery(
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        String question = body.getOrDefault("question", "");
        return aiService.nlQuery(question, userId);
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        String message = body.getOrDefault("message", "");
        return aiService.chat(message, userId);
    }

    @PostMapping(value = "/docs/{repoId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateArchDocs(
            @PathVariable Long repoId,
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return aiService.generateArchDocs(repoId, userId);
    }

    @PostMapping(value = "/onboard", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter onboardingTrace(
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        String feature = body.getOrDefault("feature", "authentication");
        return aiService.onboardingTrace(feature, userId);
    }

    @GetMapping("/anomalies")
    public ResponseEntity<List<Map<String, Object>>> detectAnomalies(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(aiService.detectAnomalies(userId));
    }

    @GetMapping("/tech-debt")
    public ResponseEntity<Map<String, Object>> getTechDebt(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(aiService.getTechDebt(userId));
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getQueryHistory(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(aiService.getQueryHistory(userId));
    }

    /** Phase 4: single narrative — not a chat session */
    @PostMapping("/explain-pr-risk")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, String>> explainPrRisk(
            @RequestBody Map<String, Object> body,
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        String verdict = String.valueOf(body.getOrDefault("verdict", ""));
        double score = body.get("numericScore") instanceof Number n ? n.doubleValue() : 0;
        String scenario = String.valueOf(body.getOrDefault("scenario", ""));
        List<String> factors = body.get("riskFactors") instanceof List<?> l
            ? l.stream().map(String::valueOf).toList() : List.of();
        String text = aiService.explainPrRisk(userId, verdict, score, scenario, factors);
        return ResponseEntity.ok(Map.of("explanation", text));
    }

    private Long extractUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
