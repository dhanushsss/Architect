package com.architect.service;

import com.architect.model.*;
import com.architect.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrgMemberRepository orgMemberRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public Organization createOrg(String name, Long creatorUserId) {
        String slug = toSlug(name);
        // ensure unique slug
        String baseSlug = slug;
        int i = 1;
        while (organizationRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + i++;
        }

        Organization org = new Organization();
        org.setName(name);
        org.setSlug(slug);
        org = organizationRepository.save(org);

        // Creator becomes ADMIN
        User creator = userRepository.findById(creatorUserId).orElseThrow();
        OrgMember member = new OrgMember();
        member.setOrganization(org);
        member.setUser(creator);
        member.setRole("ADMIN");
        orgMemberRepository.save(member);

        logAudit(creatorUserId, null, "CREATE_ORG", "ORGANIZATION", org.getId(), null);
        return org;
    }

    @Transactional
    public OrgMember addMember(Long orgId, Long targetUserId, String role, Long invitedByUserId) {
        Organization org = organizationRepository.findById(orgId).orElseThrow();
        User user = userRepository.findById(targetUserId).orElseThrow();

        if (orgMemberRepository.existsByOrganizationIdAndUserId(orgId, targetUserId)) {
            throw new IllegalStateException("User is already a member of this organization");
        }

        OrgMember member = new OrgMember();
        member.setOrganization(org);
        member.setUser(user);
        member.setRole(role != null ? role : "DEVELOPER");
        userRepository.findById(invitedByUserId).ifPresent(member::setInvitedBy);
        member = orgMemberRepository.save(member);

        logAudit(invitedByUserId, orgId, "ADD_MEMBER", "USER", targetUserId, null);
        return member;
    }

    @Transactional
    public void removeMember(Long orgId, Long targetUserId, Long removedByUserId) {
        orgMemberRepository.findByOrganizationIdAndUserId(orgId, targetUserId)
                .ifPresent(orgMemberRepository::delete);
        logAudit(removedByUserId, orgId, "REMOVE_MEMBER", "USER", targetUserId, null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMembers(Long orgId) {
        return orgMemberRepository.findByOrganizationId(orgId).stream()
                .map(m -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("userId", m.getUser().getId());
                    r.put("login", m.getUser().getLogin());
                    r.put("name", m.getUser().getName());
                    r.put("avatarUrl", m.getUser().getAvatarUrl());
                    r.put("role", m.getRole());
                    r.put("joinedAt", m.getJoinedAt());
                    return r;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Organization> getUserOrgs(Long userId) {
        return orgMemberRepository.findByUserId(userId).stream()
                .map(OrgMember::getOrganization)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public String getUserRole(Long orgId, Long userId) {
        return orgMemberRepository.findByOrganizationIdAndUserId(orgId, userId)
                .map(OrgMember::getRole)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAuditLogs(Long orgId, int page) {
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(page, 50,
                        org.springframework.data.domain.Sort.by("createdAt").descending());
        return auditLogRepository.findByOrganizationId(orgId, pageable).stream()
                .map(l -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", l.getId());
                    r.put("action", l.getAction());
                    r.put("resourceType", l.getResourceType());
                    r.put("resourceId", l.getResourceId());
                    r.put("user", l.getUser() != null ? l.getUser().getLogin() : null);
                    r.put("createdAt", l.getCreatedAt());
                    return r;
                })
                .collect(Collectors.toList());
    }

    private void logAudit(Long userId, Long orgId, String action, String resourceType,
                          Long resourceId, String details) {
        try {
            AuditLog log = new AuditLog();
            userRepository.findById(userId).ifPresent(log::setUser);
            if (orgId != null) organizationRepository.findById(orgId).ifPresent(log::setOrganization);
            log.setAction(action);
            log.setResourceType(resourceType);
            log.setResourceId(resourceId);
            log.setDetails(details);
            auditLogRepository.save(log);
        } catch (Exception e) {
            // audit log failure should not block main operation
        }
    }

    private String toSlug(String name) {
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("[^\\x00-\\x7F]", "");
        return normalized.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
