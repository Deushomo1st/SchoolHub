package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.LibraryStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LibraryStaffRepository extends JpaRepository<LibraryStaff, Long> {
    Optional<LibraryStaff> findByUserId(Long userId);
    List<LibraryStaff> findByStatus(String status);
    boolean existsByUserId(Long userId);
}
