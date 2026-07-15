package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.FeeCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeeCategoryRepository extends JpaRepository<FeeCategory, Long> {
    List<FeeCategory> findAllByOrderByNameAsc();
    Optional<FeeCategory> findByNameIgnoreCase(String name);
}
