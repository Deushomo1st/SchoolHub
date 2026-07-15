package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {
    List<Assessment> findByClassSubjectId(Long classSubjectId);
    List<Assessment> findByCohortOfferingId(Long cohortOfferingId);
}
