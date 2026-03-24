package com.architect.service;

import com.architect.config.AppProperties;
import com.architect.model.User;
import com.architect.repository.UserRepository;
import com.architect.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

/**
 * Shared OAuth completion so both {@code /api/auth/callback} and
 * {@code /api/webhooks/github} (GET, when OAuth callback URL was mis-set to webhook) work.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GithubOAuthCallbackService {

    private final AppProperties appProperties;
    private final GitHubService gitHubService;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public RedirectView completeLogin(String code) {
        try {
            String accessToken = gitHubService.exchangeCodeForToken(
                    code,
                    appProperties.getGithub().getClientId(),
                    appProperties.getGithub().getClientSecret(),
                    appProperties.getGithub().getRedirectUri());
            if (accessToken == null || accessToken.isBlank()) {
                log.warn("GitHub returned no access_token (check GITHUB_REDIRECT_URI matches GitHub OAuth callback URL)");
                return new RedirectView(appProperties.getFrontendUrl() + "/login?error=oauth_failed");
            }

            Map<String, Object> githubUser = gitHubService.getAuthenticatedUser(accessToken);

            Long githubId = ((Number) githubUser.get("id")).longValue();
            String login = (String) githubUser.get("login");
            String name = (String) githubUser.get("name");
            String avatarUrl = (String) githubUser.get("avatar_url");

            User user = userRepository.findByGithubId(githubId)
                    .map(existing -> {
                        existing.setAccessToken(accessToken);
                        existing.setName(name);
                        existing.setAvatarUrl(avatarUrl);
                        return userRepository.save(existing);
                    })
                    .orElseGet(() -> userRepository.save(User.builder()
                            .githubId(githubId)
                            .login(login)
                            .name(name)
                            .avatarUrl(avatarUrl)
                            .accessToken(accessToken)
                            .build()));

            String jwt = jwtTokenProvider.generateToken(user.getId(), user.getLogin());
            return new RedirectView(appProperties.getFrontendUrl() + "/auth/callback?token=" + jwt);
        } catch (Exception e) {
            log.error("OAuth callback failed", e);
            return new RedirectView(appProperties.getFrontendUrl() + "/login?error=oauth_failed");
        }
    }
}
