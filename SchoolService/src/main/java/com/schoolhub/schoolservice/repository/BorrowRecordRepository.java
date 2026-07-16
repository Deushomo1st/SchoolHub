package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.BorrowRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {
    List<BorrowRecord> findByLibraryStudentIdOrderByBorrowDateDesc(Long libraryStudentId);
    List<BorrowRecord> findByLibraryStudentIdAndStatus(Long libraryStudentId, String status);
    List<BorrowRecord> findByStatus(String status);
    long countByStatus(String status);
}
