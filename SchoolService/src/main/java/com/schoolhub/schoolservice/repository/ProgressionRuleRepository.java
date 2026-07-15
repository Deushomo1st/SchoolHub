package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.ProgressionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProgressionRuleRepository extends JpaRepository<ProgressionRule, Long> {
    List<ProgressionRule> findByScopeTypeAndScopeRefId(String scopeType, Long scopeRefId);
}
