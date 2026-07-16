package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.LibraryStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LibraryStudentRepository extends JpaRepository<LibraryStudent, Long> {
    Optional<LibraryStudent> findByUserId(Long userId);
    Optional<LibraryStudent> findByLibraryCode(String libraryCode);
    List<LibraryStudent> findByStatus(String status);
    boolean existsByUserId(Long userId);
}
