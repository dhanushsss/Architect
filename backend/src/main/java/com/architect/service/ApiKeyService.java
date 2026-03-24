package com.architect.service;

import com.architect.model.ApiKey;
import com.architect.repository.ApiKeyRepository;
import com.architect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private static final SecureRandom random = new SecureRandom();

    @Transactional
    public Map<String, Object> generateKey(Long userId, String name, String scopes) {
        String rawKey = generateRawKey();
        String keyHash = hashKey(rawKey);
        String prefix = rawKey.substring(0, 12);

        ApiKey apiKey = new ApiKey();
        userRepository.findById(userId).ifPresent(apiKey::setUser);
        apiKey.setName(name);
        apiKey.setKeyHash(keyHash);
        apiKey.setKeyPrefix(prefix);
        apiKey.setScopes(scopes != null ? scopes : "read:graph");
        apiKey = apiKeyRepository.save(apiKey);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", apiKey.getId());
        result.put("name", apiKey.getName());
        result.put("key", rawKey);  // Only returned once
        result.put("prefix", prefix);
        result.put("scopes", apiKey.getScopes());
        result.put("createdAt", apiKey.getCreatedAt());
        result.put("warning", "Store this key securely — it will not be shown again");
        return result;
    }

    public List<Map<String, Object>> listKeys(Long userId) {
        return apiKeyRepository.findByUserIdAndActiveTrue(userId).stream()
                .map(k -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", k.getId());
                    m.put("name", k.getName());
                    m.put("prefix", k.getKeyPrefix() + "...");
                    m.put("scopes", k.getScopes());
                    m.put("rateLimitPerHour", k.getRateLimitPerHour());
                    m.put("lastUsedAt", k.getLastUsedAt());
                    m.put("createdAt", k.getCreatedAt());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void revokeKey(Long keyId, Long userId) {
        apiKeyRepository.findById(keyId).ifPresent(key -> {
            if (key.getUser() != null && key.getUser().getId().equals(userId)) {
                key.setActive(false);
                apiKeyRepository.save(key);
            }
        });
    }

    public Optional<ApiKey> validateKey(String rawKey) {
        String keyHash = hashKey(rawKey);
        Optional<ApiKey> keyOpt = apiKeyRepository.findByKeyHashAndActiveTrue(keyHash);
        keyOpt.ifPresent(key -> {
            key.setLastUsedAt(java.time.LocalDateTime.now());
            apiKeyRepository.save(key);
        });
        return keyOpt;
    }

    private String generateRawKey() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return "arc_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash key", e);
        }
    }
}
