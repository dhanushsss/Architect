package com.architect.controller;

import com.architect.dto.RepoDto;
import com.architect.model.Repo;
import com.architect.model.User;
import com.architect.repository.ApiEndpointRepository;
import com.architect.repository.RepoRepository;
import com.architect.service.GitHubService;
import com.architect.service.RepoScannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/repos")
@RequiredArgsConstructor
public class RepoController {

    private final RepoRepository repoRepository;
    private final ApiEndpointRepository apiEndpointRepository;
    private final GitHubService gitHubService;
    private final RepoScannerService repoScannerService;

    @GetMapping
    public ResponseEntity<List<RepoDto>> listConnectedRepos(@AuthenticationPrincipal User user) {
        List<Repo> repos = repoRepository.findByUserOrderByNameAsc(user);
        List<RepoDto> dtos = repos.stream().map(r -> toDto(r, user)).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/github")
    public ResponseEntity<List<Map<String, Object>>> listGithubRepos(@AuthenticationPrincipal User user) {
        List<Map<String, Object>> repos = gitHubService.listUserRepos(user.getAccessToken());
        return ResponseEntity.ok(repos);
    }

    @PostMapping("/connect")
    public ResponseEntity<RepoDto> connectRepo(@AuthenticationPrincipal User user,
                                               @RequestBody Map<String, Object> body) {
        Long githubId = ((Number) body.get("githubId")).longValue();
        String fullName = (String) body.get("fullName");
        String name = (String) body.get("name");
        String language = (String) body.getOrDefault("language", null);
        String defaultBranch = (String) body.getOrDefault("defaultBranch", "main");
        String htmlUrl = (String) body.getOrDefault("htmlUrl", null);
        Boolean isPrivate = (Boolean) body.getOrDefault("private", false);
        String description = (String) body.getOrDefault("description", null);

        Repo repo = repoRepository.findByUserAndGithubId(user, githubId)
            .orElseGet(() -> Repo.builder()
                .user(user)
                .githubId(githubId)
                .name(name)
                .fullName(fullName)
                .description(description)
                .primaryLanguage(language)
                .defaultBranch(defaultBranch)
                .htmlUrl(htmlUrl)
                .isPrivate(isPrivate)
                .scanStatus(Repo.ScanStatus.PENDING)
                .build());

        repo = repoRepository.save(repo);
        // Change 1 — auto-scan immediately on connect (QUICK for fast first experience)
        repoScannerService.scanRepo(repo, user.getAccessToken(), RepoScannerService.ScanMode.QUICK);
        return ResponseEntity.ok(toDto(repo, user));
    }

    /**
     * Re-matches all unresolved cross-repo calls and imports for this user.
     * Call this after connecting all repos to ensure backend→backend connections appear.
     */
    @PostMapping("/relink")
    public ResponseEntity<Map<String, Object>> relinkRepos(@AuthenticationPrincipal User user) {
        repoScannerService.relinkAllRepos(user);
        return ResponseEntity.ok(Map.of("status", "relink_started",
                "message", "Cross-repo connections are being re-evaluated in the background"));
    }

    @PostMapping("/{repoId}/rescan")
    public ResponseEntity<Map<String, Object>> rescanRepo(@AuthenticationPrincipal User user,
                                                          @PathVariable Long repoId,
                                                          @RequestParam(defaultValue = "deep") String mode) {
        Repo repo = repoRepository.findById(repoId)
                .filter(r -> r.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new RuntimeException("Repo not found"));
        RepoScannerService.ScanMode scanMode = "quick".equalsIgnoreCase(mode)
                ? RepoScannerService.ScanMode.QUICK : RepoScannerService.ScanMode.DEEP;
        repoScannerService.scanRepo(repo, user.getAccessToken(), scanMode);
        return ResponseEntity.ok(Map.of("status", "scan_started", "mode", scanMode.name()));
    }

    @DeleteMapping("/{repoId}")
    public ResponseEntity<Void> disconnectRepo(@AuthenticationPrincipal User user,
                                               @PathVariable Long repoId) {
        Repo repo = repoRepository.findById(repoId)
            .filter(r -> r.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new RuntimeException("Repo not found"));
        repoRepository.delete(repo);
        return ResponseEntity.noContent().build();
    }

    private RepoDto toDto(Repo r, User user) {
        return RepoDto.builder()
            .id(r.getId())
            .githubId(r.getGithubId())
            .name(r.getName())
            .fullName(r.getFullName())
            .description(r.getDescription())
            .primaryLanguage(r.getPrimaryLanguage())
            .htmlUrl(r.getHtmlUrl())
            .isPrivate(r.getIsPrivate())
            .scanStatus(r.getScanStatus().name())
            .lastScannedAt(r.getLastScannedAt() != null ? r.getLastScannedAt().toString() : null)
            .endpointCount(apiEndpointRepository.countByRepo(r))
            .build();
    }
}
