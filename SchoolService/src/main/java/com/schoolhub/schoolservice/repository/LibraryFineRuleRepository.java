package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.LibraryFineRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LibraryFineRuleRepository extends JpaRepository<LibraryFineRule, Long> {
    Optional<LibraryFineRule> findByRuleType(String ruleType);
}
