package com.architect.controller;

import com.architect.model.Organization;
import com.architect.model.OrgMember;
import com.architect.model.User;
import com.architect.service.GovernanceService;
import com.architect.service.OrganizationService;
import com.architect.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/enterprise")
@RequiredArgsConstructor
public class EnterpriseController {

    private final OrganizationService organizationService;
    private final GovernanceService governanceService;
    private final SnapshotService snapshotService;

    @PostMapping("/orgs")
    public ResponseEntity<Organization> createOrg(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(organizationService.createOrg(body.get("name"), user.getId()));
    }

    @GetMapping("/orgs")
    public ResponseEntity<List<Organization>> getMyOrgs(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(organizationService.getUserOrgs(user.getId()));
    }

    @GetMapping("/orgs/{orgId}/members")
    public ResponseEntity<List<Map<String, Object>>> getMembers(@PathVariable Long orgId) {
        return ResponseEntity.ok(organizationService.getMembers(orgId));
    }

    @PostMapping("/orgs/{orgId}/members")
    public ResponseEntity<OrgMember> addMember(
            @PathVariable Long orgId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User user) {
        Long targetUserId = Long.parseLong(body.get("userId").toString());
        String role = (String) body.getOrDefault("role", "DEVELOPER");
        return ResponseEntity.ok(organizationService.addMember(orgId, targetUserId, role, user.getId()));
    }

    @DeleteMapping("/orgs/{orgId}/members/{targetUserId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long orgId,
            @PathVariable Long targetUserId,
            @AuthenticationPrincipal User user) {
        organizationService.removeMember(orgId, targetUserId, user.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/governance")
    public ResponseEntity<Map<String, Object>> getGovernance(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(governanceService.getGovernanceDashboard(user.getId()));
    }

    @GetMapping("/audit/soc2")
    public ResponseEntity<Map<String, Object>> getSoc2Report(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(governanceService.getSocAuditReport(user.getId()));
    }

    @GetMapping("/orgs/{orgId}/audit")
    public ResponseEntity<List<Map<String, Object>>> getAuditLogs(
            @PathVariable Long orgId,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(organizationService.getAuditLogs(orgId, page));
    }

    @PostMapping("/snapshots")
    public ResponseEntity<Map<String, Object>> createSnapshot(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user) {
        String label = body.getOrDefault("label", "Snapshot " + java.time.LocalDateTime.now());
        return ResponseEntity.ok(snapshotService.createSnapshot(user.getId(), label));
    }

    @GetMapping("/snapshots")
    public ResponseEntity<List<Map<String, Object>>> listSnapshots(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(snapshotService.listSnapshots(user.getId()));
    }

    @GetMapping("/snapshots/{snapshotId}/diff/{compareId}")
    public ResponseEntity<Map<String, Object>> diffSnapshots(
            @PathVariable Long snapshotId,
            @PathVariable Long compareId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(snapshotService.diffSnapshots(snapshotId, compareId));
    }
}
