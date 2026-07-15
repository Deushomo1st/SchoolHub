package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    List<Student> findAllByOrderByLastNameAscFirstNameAsc();
    List<Student> findByClassId(Long classId);
    Optional<Student> findByUserId(Long userId);
    boolean existsByAdmissionNo(String admissionNo);
}
