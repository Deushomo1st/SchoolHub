package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.Offering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfferingRepository extends JpaRepository<Offering, Long> {
    List<Offering> findAllByOrderByTitleAsc();
    List<Offering> findByParentIdIsNullOrderByTitleAsc();
    List<Offering> findByParentIdOrderByTitleAsc(Long parentId);
}
