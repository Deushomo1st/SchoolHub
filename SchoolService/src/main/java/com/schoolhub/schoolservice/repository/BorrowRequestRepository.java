package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.BorrowRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BorrowRequestRepository extends JpaRepository<BorrowRequest, Long> {
    List<BorrowRequest> findByStatus(String status);
    List<BorrowRequest> findByLibraryStudentIdOrderByRequestedAtDesc(Long libraryStudentId);
    List<BorrowRequest> findByLibraryStudentIdAndStatus(Long libraryStudentId, String status);
    long countByStatus(String status);
}
