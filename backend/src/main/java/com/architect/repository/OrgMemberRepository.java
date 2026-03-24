package com.architect.repository;

import com.architect.model.OrgMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrgMemberRepository extends JpaRepository<OrgMember, Long> {
    List<OrgMember> findByOrganizationId(Long orgId);
    List<OrgMember> findByUserId(Long userId);
    Optional<OrgMember> findByOrganizationIdAndUserId(Long orgId, Long userId);
    boolean existsByOrganizationIdAndUserId(Long orgId, Long userId);
}
