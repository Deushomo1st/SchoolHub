package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {
    List<Group> findByCohortId(Long cohortId);
}
