package com.architect.controller;

import com.architect.config.AppProperties;
import com.architect.dto.GraphDto;
import com.architect.model.ApiKey;
import com.architect.service.ApiKeyService;
import com.architect.service.GraphBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public/v1")
@RequiredArgsConstructor
public class PublicApiController {

    private final ApiKeyService apiKeyService;
    private final GraphBuilderService graphBuilderService;
    private final AppProperties appProperties;

    @GetMapping("/graph/{owner}")
    public ResponseEntity<?> getGraph(
            @PathVariable String owner,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "X-API-Key header required"));
        }
        return apiKeyService.validateKey(apiKey)
                .map(key -> {
                    // Use the key owner's graph
                    GraphDto graph = graphBuilderService.buildGraph(key.getUser());
                    return ResponseEntity.ok(graph);
                })
                .orElseGet(() -> ResponseEntity.status(401).body(null));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "version", appProperties.getProductVersion()));
    }
}
