package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.Result;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResultRepository extends JpaRepository<Result, Long> {
    List<Result> findByStudentId(Long studentId);
    List<Result> findByAssessmentId(Long assessmentId);
    Optional<Result> findByAssessmentIdAndStudentId(Long assessmentId, Long studentId);
}
