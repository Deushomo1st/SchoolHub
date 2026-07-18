package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.ClassGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassGroupRepository extends JpaRepository<ClassGroup, Long> {
    List<ClassGroup> findByClassIdOrderByNameAsc(Long classId);
    boolean existsByClassIdAndNameIgnoreCase(Long classId, String name);
}
