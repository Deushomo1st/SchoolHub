package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.Cohort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CohortRepository extends JpaRepository<Cohort, Long> {
    List<Cohort> findAllByOrderByNameAsc();
    List<Cohort> findBySchedulePeriodId(Long schedulePeriodId);
    boolean existsBySchedulePeriodIdAndName(Long schedulePeriodId, String name);
}
