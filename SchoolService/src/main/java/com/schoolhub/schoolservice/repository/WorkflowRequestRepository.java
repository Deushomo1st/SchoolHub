package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.WorkflowRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkflowRequestRepository extends JpaRepository<WorkflowRequest, Long> {
    List<WorkflowRequest> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    List<WorkflowRequest> findByStateAndProtestDeadlineLessThanEqual(String state, LocalDateTime now);
}
