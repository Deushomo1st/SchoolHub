package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.BorrowRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {
    List<BorrowRecord> findByLibraryStudentIdOrderByBorrowDateDesc(Long libraryStudentId);
    List<BorrowRecord> findByLibraryStudentIdAndStatus(Long libraryStudentId, String status);
    List<BorrowRecord> findByStatus(String status);
    long countByStatus(String status);
    
    @Query("SELECT COUNT(b) FROM BorrowRecord b WHERE b.libraryStudentId = :libraryStudentId AND b.status IN ('active', 'overdue')")
    long countActiveBorrows(@Param("libraryStudentId") Long libraryStudentId);
    
    @Query("SELECT COALESCE(SUM(b.fineCharged), 0) FROM BorrowRecord b WHERE b.libraryStudentId = :libraryStudentId AND b.status = 'overdue' AND b.finePaid = false")
    BigDecimal sumUnpaidFines(@Param("libraryStudentId") Long libraryStudentId);
}
