package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.CohortOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CohortOfferingRepository extends JpaRepository<CohortOffering, Long> {
    List<CohortOffering> findByCohortId(Long cohortId);
    List<CohortOffering> findByTeacherId(Long teacherId);
    boolean existsByCohortIdAndOfferingId(Long cohortId, Long offeringId);
}
