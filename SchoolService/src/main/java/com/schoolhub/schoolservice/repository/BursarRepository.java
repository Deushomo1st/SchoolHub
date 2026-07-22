package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.Bursar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BursarRepository extends JpaRepository<Bursar, Long> {
    boolean existsByStaffNo(String staffNo);
    Optional<Bursar> findByUserId(Long userId);
}
