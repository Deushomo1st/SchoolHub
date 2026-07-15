package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.SchedulePeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchedulePeriodRepository extends JpaRepository<SchedulePeriod, Long> {
    List<SchedulePeriod> findAllByOrderByStartDateDesc();
    List<SchedulePeriod> findByParentIdIsNullOrderByStartDateDesc();
    List<SchedulePeriod> findByParentId(Long parentId);
}
