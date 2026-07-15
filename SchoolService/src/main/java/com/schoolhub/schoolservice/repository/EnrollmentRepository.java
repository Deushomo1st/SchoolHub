package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    // A student can hold several simultaneously-active Enrollments (a home cohort plus
    // separate elective Offerings/Groups) - so "by student" queries return a List, never Optional.
    List<Enrollment> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<Enrollment> findByStudentIdAndStatus(Long studentId, String status);
    List<Enrollment> findByCohortId(Long cohortId);

    // The one enrollment (if any) binding this student to a home Cohort right now - used to
    // keep Student.classId dual-written and to drive the dashboards' Enrollment-aware read path.
    Optional<Enrollment> findFirstByStudentIdAndCohortIdIsNotNullAndStatusOrderByCreatedAtDesc(Long studentId, String status);
}
