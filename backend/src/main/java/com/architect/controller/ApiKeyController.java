package com.architect.controller;

import com.architect.security.JwtTokenProvider;
import com.architect.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping
    public ResponseEntity<Map<String, Object>> generateKey(
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String auth) {
        Long userId = extractUserId(auth);
        String name = body.getOrDefault("name", "My API Key");
        String scopes = body.getOrDefault("scopes", "read:graph");
        return ResponseEntity.ok(apiKeyService.generateKey(userId, name, scopes));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listKeys(
            @RequestHeader("Authorization") String auth) {
        Long userId = extractUserId(auth);
        return ResponseEntity.ok(apiKeyService.listKeys(userId));
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> revokeKey(
            @PathVariable Long keyId,
            @RequestHeader("Authorization") String auth) {
        Long userId = extractUserId(auth);
        apiKeyService.revokeKey(keyId, userId);
        return ResponseEntity.ok().build();
    }

    private Long extractUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
