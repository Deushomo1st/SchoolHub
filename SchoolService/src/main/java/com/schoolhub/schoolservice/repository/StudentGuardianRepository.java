package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.StudentGuardian;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentGuardianRepository extends JpaRepository<StudentGuardian, Long> {
    List<StudentGuardian> findByGuardianId(Long guardianId);
    List<StudentGuardian> findByStudentId(Long studentId);
}
