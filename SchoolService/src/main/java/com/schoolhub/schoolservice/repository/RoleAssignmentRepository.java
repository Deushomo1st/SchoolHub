package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.RoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, Long> {
    List<RoleAssignment> findByAppUserIdAndStatus(Long appUserId, String status);

    // "Everyone in the branch" for a deletion notify step: every active assignment scoped to
    // one of these Org Unit ids (scope_ref_id is a raw cross-schema id, see model comment).
    List<RoleAssignment> findByScopeTypeAndScopeRefIdInAndStatus(String scopeType, List<Long> scopeRefIds, String status);
}
