package com.architect.controller;

import com.architect.model.Repo;
import com.architect.model.User;
import com.architect.repository.ApiCallRepository;
import com.architect.repository.ApiEndpointRepository;
import com.architect.repository.RepoRepository;
import com.architect.service.RuntimeWiringGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Architecture snapshot metrics (aligned with dependency graph where applicable). */
@RestController
@RequestMapping("/api/v1/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final RepoRepository repoRepository;
    private final ApiEndpointRepository apiEndpointRepository;
    private final ApiCallRepository apiCallRepository;
    private final RuntimeWiringGraphService runtimeWiringGraphService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(@AuthenticationPrincipal User user) {
        List<Repo> repos = repoRepository.findByUserId(user.getId());
        long totalEndpoints = 0;
        long staleRepos = 0;
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        for (Repo r : repos) {
            totalEndpoints += apiEndpointRepository.countByRepo(r);
            if (r.getLastScannedAt() == null || r.getLastScannedAt().isBefore(weekAgo)) {
                staleRepos++;
            }
        }
        long crossRepoPairs = apiCallRepository.countDistinctCrossRepoCallPairsForUser(user.getId());
        long runtimeWires = runtimeWiringGraphService.buildWiredEdges(user, repos).size();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("connectedRepos", repos.size());
        m.put("indexedEndpoints", totalEndpoints);
        m.put("trackedCrossRepoCallEdges", crossRepoPairs);
        m.put("runtimeCrossRepoWires", runtimeWires);
        m.put("reposNeedingRescan", staleRepos);
        m.put("generatedAt", LocalDateTime.now().toString());
        return ResponseEntity.ok(m);
    }
}
