package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.ResourcePermit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourcePermitRepository extends JpaRepository<ResourcePermit, Long> {
    boolean existsByResourceIdAndUserId(Long resourceId, Long userId);
}
