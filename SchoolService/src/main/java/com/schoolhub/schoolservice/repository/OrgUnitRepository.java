package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.OrgUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, Long> {
    List<OrgUnit> findAllByOrderByNameAsc();
    List<OrgUnit> findByParentIdIsNullOrderByNameAsc();
    List<OrgUnit> findByParentIdOrderByNameAsc(Long parentId);
}
