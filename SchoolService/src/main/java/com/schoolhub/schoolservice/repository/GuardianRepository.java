package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.Guardian;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {
    List<Guardian> findAllByOrderByLastNameAscFirstNameAsc();
    Optional<Guardian> findByUserId(Long userId);
}
