package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.OfferingOrgUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfferingOrgUnitRepository extends JpaRepository<OfferingOrgUnit, Long> {
    List<OfferingOrgUnit> findByOfferingId(Long offeringId);
    List<OfferingOrgUnit> findByOrgUnitId(Long orgUnitId);
    Optional<OfferingOrgUnit> findByOfferingIdAndOrgUnitId(Long offeringId, Long orgUnitId);
    boolean existsByOfferingIdAndOrgUnitId(Long offeringId, Long orgUnitId);
}
