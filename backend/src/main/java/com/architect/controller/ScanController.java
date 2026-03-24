package com.architect.controller;

import com.architect.dto.ScanStatusDto;
import com.architect.model.Repo;
import com.architect.model.User;
import com.architect.repository.ApiCallRepository;
import com.architect.repository.ApiEndpointRepository;
import com.architect.repository.ComponentImportRepository;
import com.architect.repository.RepoRepository;
import com.architect.service.RepoScannerService;
import com.architect.service.ScanProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/scan")
@RequiredArgsConstructor
public class ScanController {

    private final RepoRepository repoRepository;
    private final RepoScannerService repoScannerService;
    private final ScanProgressService scanProgressService;
    private final ApiEndpointRepository apiEndpointRepository;
    private final ApiCallRepository apiCallRepository;
    private final ComponentImportRepository componentImportRepository;

    /** Change 4 — optional ?mode=quick|deep (default: deep) */
    @PostMapping("/{repoId}")
    public ResponseEntity<ScanStatusDto> triggerScan(
            @AuthenticationPrincipal User user,
            @PathVariable Long repoId,
            @RequestParam(defaultValue = "deep") String mode) {

        Repo repo = repoRepository.findById(repoId)
            .filter(r -> r.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new RuntimeException("Repo not found"));

        RepoScannerService.ScanMode scanMode =
            "quick".equalsIgnoreCase(mode) ? RepoScannerService.ScanMode.QUICK : RepoScannerService.ScanMode.DEEP;

        repoScannerService.scanRepo(repo, user.getAccessToken(), scanMode);

        return ResponseEntity.accepted().body(ScanStatusDto.builder()
            .repoId(repo.getId())
            .repoName(repo.getName())
            .status("SCANNING")
            .message(scanMode.name() + " scan started")
            .build());
    }

    /** Change 4 — ?mode=quick|deep applies to all repos */
    @PostMapping("/all")
    public ResponseEntity<List<ScanStatusDto>> scanAll(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "deep") String mode) {

        RepoScannerService.ScanMode scanMode =
            "quick".equalsIgnoreCase(mode) ? RepoScannerService.ScanMode.QUICK : RepoScannerService.ScanMode.DEEP;

        List<ScanStatusDto> statuses = repoRepository.findByUser(user).stream().map(repo -> {
            repoScannerService.scanRepo(repo, user.getAccessToken(), scanMode);
            return ScanStatusDto.builder()
                .repoId(repo.getId())
                .repoName(repo.getName())
                .status("SCANNING")
                .message(scanMode.name() + " scan queued")
                .build();
        }).toList();
        return ResponseEntity.accepted().body(statuses);
    }

    /** Change 2 — SSE stream: subscribe to live scan progress for a repo */
    @GetMapping(value = "/{repoId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamScanProgress(
            @AuthenticationPrincipal User user,
            @PathVariable Long repoId) {

        // Verify ownership
        repoRepository.findById(repoId)
            .filter(r -> r.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new RuntimeException("Repo not found"));

        return scanProgressService.subscribe(repoId);
    }

    @GetMapping("/{repoId}/status")
    public ResponseEntity<ScanStatusDto> getScanStatus(
            @AuthenticationPrincipal User user,
            @PathVariable Long repoId) {

        Repo repo = repoRepository.findById(repoId)
            .filter(r -> r.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new RuntimeException("Repo not found"));

        return ResponseEntity.ok(ScanStatusDto.builder()
            .repoId(repo.getId())
            .repoName(repo.getName())
            .status(repo.getScanStatus().name())
            .endpointsFound((int) apiEndpointRepository.countByRepo(repo))
            .callsFound(apiCallRepository.findByCallerRepo(repo).size())
            .importsFound(componentImportRepository.findBySourceRepo(repo).size())
            .build());
    }
}
