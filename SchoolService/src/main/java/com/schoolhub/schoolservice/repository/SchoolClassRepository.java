package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {
    List<SchoolClass> findAllByOrderByNameAsc();
}
