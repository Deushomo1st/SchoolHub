package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    List<Teacher> findAllByOrderByLastNameAscFirstNameAsc();
    boolean existsByStaffNo(String staffNo);
    Optional<Teacher> findByUserId(Long userId);
}
