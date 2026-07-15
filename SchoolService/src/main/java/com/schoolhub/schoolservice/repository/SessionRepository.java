package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionRepository extends JpaRepository<Session, Long> {
    List<Session> findByCohortOfferingIdOrderByStartAtDesc(Long cohortOfferingId);
    List<Session> findByResourceIdAndStatusNot(Long resourceId, String status);
}
