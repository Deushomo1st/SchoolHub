package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.WorkflowProtest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowProtestRepository extends JpaRepository<WorkflowProtest, Long> {
    List<WorkflowProtest> findByWorkflowRequestId(Long workflowRequestId);
    List<WorkflowProtest> findByWorkflowRequestIdAndStatus(Long workflowRequestId, String status);
}
