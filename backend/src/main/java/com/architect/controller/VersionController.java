package com.architect.controller;

import com.architect.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Unauthenticated product version (login screen, navbar, integrations).
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class VersionController {

    private final AppProperties appProperties;

    @GetMapping("/version")
    public ResponseEntity<Map<String, String>> version() {
        return ResponseEntity.ok(Map.of(
                "product", "Architect",
                "version", appProperties.getProductVersion(),
                "publicApi", "v1"
        ));
    }
}
