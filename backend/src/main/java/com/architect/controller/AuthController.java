package com.architect.controller;

import com.architect.config.AppProperties;
import com.architect.model.User;
import com.architect.service.GithubOAuthCallbackService;
import com.architect.service.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AppProperties appProperties;
    private final GitHubService gitHubService;
    private final GithubOAuthCallbackService githubOAuthCallbackService;

    @GetMapping("/github")
    public RedirectView initiateOAuth() {
        String clientId = appProperties.getGithub().getClientId();
        String secret = appProperties.getGithub().getClientSecret();
        if (clientId == null || clientId.isBlank()
                || secret == null || secret.isBlank()
                || clientId.contains("{") || clientId.contains("${")) {
            log.warn("GitHub OAuth not configured (set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET)");
            return new RedirectView(appProperties.getFrontendUrl() + "/login?error=github_not_configured");
        }
        String url = "https://github.com/login/oauth/authorize"
                + "?client_id=" + java.net.URLEncoder.encode(clientId, java.nio.charset.StandardCharsets.UTF_8)
                + "&scope=repo,read:org,read:user"
                + "&redirect_uri=" + java.net.URLEncoder.encode(
                        appProperties.getGithub().getRedirectUri(), java.nio.charset.StandardCharsets.UTF_8);
        return new RedirectView(url, false, false, false);
    }

    @GetMapping("/callback")
    public RedirectView handleCallback(@RequestParam String code) {
        return githubOAuthCallbackService.completeLogin(code);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "unauthorized",
                    "message", "Missing or invalid session — sign in with GitHub again."));
        }
        return ResponseEntity.ok(Map.of(
            "id", user.getId(),
            "githubId", user.getGithubId(),
            "login", user.getLogin(),
            "name", user.getName() != null ? user.getName() : user.getLogin(),
            "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : ""
        ));
    }
}
