package com.schoolhub.authservice.repository;

import com.schoolhub.authservice.model.RoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, Long> {
    Optional<RoleAssignment> findByAppUserIdAndIsDefaultTrueAndStatus(Long appUserId, String status);
    List<RoleAssignment> findByAppUserIdAndStatus(Long appUserId, String status);
}
