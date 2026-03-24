package com.architect.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubService {

    private final WebClient githubWebClient;

    public Map<String, Object> getAuthenticatedUser(String accessToken) {
        return githubWebClient.get()
            .uri("/user")
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .bodyToMono(Map.class)
            .block();
    }

    /**
     * Exchanges OAuth code. {@code redirectUri} must exactly match the redirect_uri used in
     * /login/oauth/authorize (and the callback URL registered on the GitHub OAuth app).
     */
    public String exchangeCodeForToken(String code, String clientId, String clientSecret, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        if (redirectUri != null && !redirectUri.isBlank()) {
            form.add("redirect_uri", redirectUri);
        }
        return WebClient.builder().build()
            .post()
            .uri("https://github.com/login/oauth/access_token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .header("Accept", "application/json")
            .body(BodyInserters.fromFormData(form))
            .retrieve()
            .bodyToMono(Map.class)
            .map(r -> {
                if (r.get("error") != null) {
                    log.warn("GitHub token error: {} — {}", r.get("error"), r.get("error_description"));
                }
                return (String) r.get("access_token");
            })
            .block();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listUserRepos(String accessToken) {
        List<Map<String, Object>> all = new ArrayList<>();
        int page = 1;
        while (true) {
            List<Map<String, Object>> batch = githubWebClient.get()
                .uri("/user/repos?per_page=100&page=" + page + "&sort=updated&affiliation=owner,collaborator,organization_member")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToFlux(Map.class)
                .cast(Map.class)
                .map(m -> (Map<String, Object>) m)
                .collectList()
                .block();
            if (batch == null || batch.isEmpty()) break;
            all.addAll(batch);
            if (batch.size() < 100) break;
            page++;
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getRepoTree(String accessToken, String owner, String repo, String branch) {
        try {
            Map<String, Object> response = githubWebClient.get()
                .uri("/repos/" + owner + "/" + repo + "/git/trees/" + branch + "?recursive=1")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            if (response == null) return Collections.emptyList();
            return (List<Map<String, Object>>) response.getOrDefault("tree", Collections.emptyList());
        } catch (Exception e) {
            log.warn("Failed to get tree for {}/{}: {}", owner, repo, e.getMessage());
            return Collections.emptyList();
        }
    }

    public String getFileContent(String accessToken, String owner, String repo, String path) {
        return getFileContentAtRef(accessToken, owner, repo, path, null);
    }

    /**
     * File content at a specific ref (branch, tag, or commit SHA). Use PR head SHA for PR diffs.
     */
    public String getFileContentAtRef(String accessToken, String owner, String repo, String path, String ref) {
        try {
            String encoded = Arrays.stream(path.split("/"))
                .map(p -> UriUtils.encodePathSegment(p, StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "/" + b)
                .orElse("");
            String uri = "/repos/" + owner + "/" + repo + "/contents/" + encoded;
            if (ref != null && !ref.isBlank()) {
                uri += "?ref=" + UriUtils.encodeQueryParam(ref, StandardCharsets.UTF_8);
            }
            byte[] bytes = githubWebClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github.raw")
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
            return bytes != null ? new String(bytes) : "";
        } catch (Exception e) {
            log.debug("Could not fetch file {}/{}/{} @ {}: {}", owner, repo, path, ref, e.getMessage());
            return "";
        }
    }

    /**
     * Commit status on PR head — optional merge blocking via branch protection.
     */
    public void createCommitStatus(String accessToken, String owner, String repo, String sha,
                                   String state, String description, String targetUrl, String context) {
        if (sha == null || sha.isBlank()) return;
        try {
            githubWebClient.post()
                .uri("/repos/" + owner + "/" + repo + "/statuses/" + sha)
                .header("Authorization", "Bearer " + accessToken)
                .bodyValue(Map.of(
                    "state", state,
                    "description", description != null && description.length() <= 140 ? description : description.substring(0, 137) + "...",
                    "context", context != null ? context : "architect/pr-impact",
                    "target_url", targetUrl != null ? targetUrl : ""
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        } catch (Exception e) {
            log.warn("Failed to post commit status: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPullRequest(String accessToken, String owner, String repo, int prNumber) {
        try {
            List<Map<String, Object>> pr = githubWebClient.get()
                .uri("/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/files")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToFlux(Map.class)
                .cast(Map.class)
                .map(m -> (Map<String, Object>) m)
                .collectList()
                .block();
            return pr != null ? pr : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Failed to get PR files: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public void createPrComment(String accessToken, String owner, String repo, int prNumber, String body) {
        try {
            githubWebClient.post()
                .uri("/repos/" + owner + "/" + repo + "/issues/" + prNumber + "/comments")
                .header("Authorization", "Bearer " + accessToken)
                .bodyValue(Map.of("body", body))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        } catch (Exception e) {
            log.warn("Failed to post PR comment: {}", e.getMessage());
        }
    }
}
