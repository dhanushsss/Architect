package com.architect.service;

import com.architect.github.GitHubRateLimitMonitor;
import com.architect.github.RateLimitExceededException;
import com.architect.model.GitHubEtagCache;
import com.architect.model.Repo;
import com.architect.repository.GitHubEtagCacheRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubService {

    private final WebClient githubWebClient;
    private final GitHubEtagCacheRepository etagCacheRepository;
    private final ObjectMapper objectMapper;
    private final GitHubRateLimitMonitor rateLimitMonitor;

    public Map<String, Object> getAuthenticatedUser(String accessToken) {
        return githubWebClient.get()
            .uri("/user")
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .bodyToMono(Map.class)
            .block();
    }

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
    /** Pass {@code repo} for ETag tree cache without an extra {@code repos} table lookup. */
    public List<Map<String, Object>> getRepoTree(String accessToken, Repo repo, String owner, String repoName, String branch) {
        String uri = "/repos/" + owner + "/" + repoName + "/git/trees/" + branch + "?recursive=1";
        String resourcePath = "tree:" + branch;
        Optional<Repo> repoOpt = Optional.ofNullable(repo);
        Optional<GitHubEtagCache> cached = repoOpt.flatMap(r -> etagCacheRepository.findByRepoAndResourcePath(r, resourcePath));

        try {
            if (repoOpt.isEmpty()) {
                return fetchTreeNoCache(accessToken, uri);
            }
            WebClient.RequestHeadersSpec<?> spec = githubWebClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + accessToken);
            if (cached.isPresent() && cached.get().getEtag() != null) {
                spec = spec.header(HttpHeaders.IF_NONE_MATCH, cached.get().getEtag());
                log.debug("GitHub tree If-None-Match repoId={} resource={}", repo.getId(), resourcePath);
            }

            List<Map<String, Object>> result = spec.exchangeToMono(response -> {
                recordRateHeaders(response);
                HttpStatus st = HttpStatus.valueOf(response.statusCode().value());
                if (st == HttpStatus.NOT_MODIFIED && cached.isPresent() && cached.get().getCachedBody() != null) {
                    log.debug("GitHub tree 304 cache hit repoId={} branch={}", repo.getId(), branch);
                    try {
                        List<Map<String, Object>> tree = objectMapper.readValue(
                            cached.get().getCachedBody(), new TypeReference<ArrayList<Map<String, Object>>>() {});
                        return Mono.<List<Map<String, Object>>>just(tree);
                    } catch (Exception e) {
                        log.warn("Failed to parse cached tree: {}", e.getMessage());
                        return emptyTreeMono();
                    }
                }
                if (st == HttpStatus.NOT_MODIFIED) {
                    return emptyTreeMono();
                }
                if (st.is2xxSuccessful()) {
                    return response.bodyToMono(Map.class).map(body -> {
                        List<Map<String, Object>> tree = extractTreeEntries(body);
                        String etag = response.headers().asHttpHeaders().getFirst(HttpHeaders.ETAG);
                        try {
                            String json = objectMapper.writeValueAsString(tree);
                            Repo r = repoOpt.get();
                            GitHubEtagCache row = cached.orElseGet(() -> GitHubEtagCache.builder().repo(r).resourcePath(resourcePath).build());
                            row.setEtag(etag);
                            row.setCachedBody(json);
                            row.setLastFetchedAt(LocalDateTime.now());
                            etagCacheRepository.save(row);
                            log.debug("GitHub tree 200 stored etag repoId={} branch={}", repo.getId(), branch);
                        } catch (Exception e) {
                            log.warn("Failed to persist tree cache: {}", e.getMessage());
                        }
                        return tree;
                    });
                }
                return emptyTreeMono();
            }).block();
            rateLimitMonitor.checkRemainingAfterResponse();
            return result != null ? result : Collections.emptyList();
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to get tree for {}/{}: {}", owner, repoName, e.getMessage());
            return Collections.emptyList();
        }
    }

    private static Mono<List<Map<String, Object>>> emptyTreeMono() {
        return Mono.just(Collections.<Map<String, Object>>emptyList());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractTreeEntries(Object body) {
        if (!(body instanceof Map<?, ?> map)) {
            return Collections.emptyList();
        }
        Object t = map.get("tree");
        if (!(t instanceof List<?> raw)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : raw) {
            if (o instanceof Map) {
                out.add((Map<String, Object>) o);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchTreeNoCache(String accessToken, String uri) {
        try {
            Map<String, Object> response = githubWebClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            if (response == null) return Collections.emptyList();
            return extractTreeEntries(response);
        } catch (Exception e) {
            log.warn("Failed to get tree: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Pass {@code repo} so ETag cache lookups avoid a DB round-trip per file (scanner hot path). */
    public String getFileContent(String accessToken, Repo repo, String owner, String repoName, String path) {
        return getFileContentAtRef(accessToken, repo, owner, repoName, path, null);
    }

    public String getFileContentAtRef(String accessToken, Repo repo, String owner, String repoName, String path, String ref) {
        try {
            String encoded = Arrays.stream(path.split("/"))
                .map(p -> UriUtils.encodePathSegment(p, StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "/" + b)
                .orElse("");
            String uri = "/repos/" + owner + "/" + repoName + "/contents/" + encoded;
            if (ref != null && !ref.isBlank()) {
                uri += "?ref=" + UriUtils.encodeQueryParam(ref, StandardCharsets.UTF_8);
            }
            String resourcePath = "file:" + path + (ref != null ? "@" + ref : "");
            Optional<Repo> repoOpt = Optional.ofNullable(repo);
            Optional<GitHubEtagCache> cached = repoOpt.flatMap(r -> etagCacheRepository.findByRepoAndResourcePath(r, resourcePath));

            if (repoOpt.isEmpty()) {
                return fetchRawBytesNoEtag(accessToken, uri);
            }

            WebClient.RequestHeadersSpec<?> spec = githubWebClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github.raw");
            if (cached.isPresent() && cached.get().getEtag() != null) {
                spec = spec.header(HttpHeaders.IF_NONE_MATCH, cached.get().getEtag());
                log.debug("GitHub file If-None-Match repoId={} path={}", repo.getId(), path);
            }

            String out = spec.exchangeToMono(response -> {
                recordRateHeaders(response);
                HttpStatus st = HttpStatus.valueOf(response.statusCode().value());
                if (st == HttpStatus.NOT_MODIFIED && cached.isPresent() && cached.get().getCachedBody() != null) {
                    log.debug("GitHub file 304 cache hit repoId={} path={}", repo.getId(), path);
                    return Mono.just(cached.get().getCachedBody());
                }
                if (st.is2xxSuccessful()) {
                    return response.bodyToMono(byte[].class).map(bytes -> {
                        String text = bytes != null ? new String(bytes) : "";
                        String etag = response.headers().asHttpHeaders().getFirst(HttpHeaders.ETAG);
                        try {
                            Repo r = repoOpt.get();
                            GitHubEtagCache row = cached.orElseGet(() -> GitHubEtagCache.builder().repo(r).resourcePath(resourcePath).build());
                            row.setEtag(etag);
                            row.setCachedBody(text);
                            row.setLastFetchedAt(LocalDateTime.now());
                            etagCacheRepository.save(row);
                            log.debug("GitHub file 200 stored etag repoId={} path={}", repo.getId(), path);
                        } catch (Exception e) {
                            log.warn("Failed to persist file cache: {}", e.getMessage());
                        }
                        return text;
                    });
                }
                return Mono.just("");
            }).block();
            rateLimitMonitor.checkRemainingAfterResponse();
            return out != null ? out : "";
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Could not fetch file {}/{}/{} @ {}: {}", owner, repoName, path, ref, e.getMessage());
            return "";
        }
    }

    private void recordRateHeaders(ClientResponse response) {
        HttpHeaders h = response.headers().asHttpHeaders();
        rateLimitMonitor.recordFromHeaders(
            h.getFirst("X-RateLimit-Remaining"),
            h.getFirst("X-RateLimit-Reset"));
    }

    private String fetchRawBytesNoEtag(String accessToken, String uri) {
        try {
            byte[] bytes = githubWebClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github.raw")
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
            return bytes != null ? new String(bytes) : "";
        } catch (Exception e) {
            log.debug("Could not fetch file (no etag): {}", e.getMessage());
            return "";
        }
    }

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
                    "context", context != null ? context : "zerqis/pr-impact",
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
        List<Map<String, Object>> all = new ArrayList<>();
        int page = 1;
        try {
            while (true) {
                List<Map<String, Object>> batch = githubWebClient.get()
                    .uri("/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/files?per_page=100&page=" + page)
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
        } catch (Exception e) {
            log.warn("Failed to get PR files: {}", e.getMessage());
            return all.isEmpty() ? Collections.emptyList() : all;
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

    /**
     * SHA of the tip commit on {@code branch}.
     */
    @SuppressWarnings("unchecked")
    public String getBranchHeadSha(String accessToken, String owner, String repo, String branch) {
        try {
            Map<String, Object> body = githubWebClient.get()
                .uri("/repos/" + owner + "/" + repo + "/branches/" + branch)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            if (body == null) return null;
            Map<String, Object> commit = (Map<String, Object>) body.get("commit");
            if (commit == null) return null;
            return (String) commit.get("sha");
        } catch (Exception e) {
            log.warn("Failed to resolve branch SHA: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Files changed between two commits (inclusive of head).
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> compareCommits(String accessToken, String owner, String repo,
                                                    String baseSha, String headSha) {
        try {
            String range = baseSha + "..." + headSha;
            Map<String, Object> body = githubWebClient.get()
                .uri("/repos/" + owner + "/" + repo + "/compare/" + range)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            if (body == null) return Collections.emptyList();
            return (List<Map<String, Object>>) body.getOrDefault("files", Collections.emptyList());
        } catch (Exception e) {
            log.warn("Failed to compare commits: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
