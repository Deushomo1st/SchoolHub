package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.OfferingPrerequisite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfferingPrerequisiteRepository extends JpaRepository<OfferingPrerequisite, Long> {
    List<OfferingPrerequisite> findByOfferingId(Long offeringId);
    Optional<OfferingPrerequisite> findByOfferingIdAndPrerequisiteId(Long offeringId, Long prerequisiteId);
    boolean existsByOfferingIdAndPrerequisiteId(Long offeringId, Long prerequisiteId);
}
