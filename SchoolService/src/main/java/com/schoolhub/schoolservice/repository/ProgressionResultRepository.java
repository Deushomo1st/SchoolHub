package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.ProgressionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProgressionResultRepository extends JpaRepository<ProgressionResult, Long> {
    List<ProgressionResult> findByEnrollmentId(Long enrollmentId);
    Optional<ProgressionResult> findByRuleIdAndEnrollmentId(Long ruleId, Long enrollmentId);
}
