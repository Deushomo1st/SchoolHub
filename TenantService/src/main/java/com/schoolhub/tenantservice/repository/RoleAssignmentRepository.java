package com.schoolhub.tenantservice.repository;

import com.schoolhub.tenantservice.model.RoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, Long> {
    List<RoleAssignment> findByAppUserIdAndStatus(Long appUserId, String status);
    List<RoleAssignment> findByTenantIdAndStatus(Long tenantId, String status);
    Optional<RoleAssignment> findByAppUserIdAndIsDefaultTrueAndStatus(Long appUserId, String status);
}
