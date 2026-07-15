package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.FeeInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeeInvoiceRepository extends JpaRepository<FeeInvoice, Long> {
    List<FeeInvoice> findAllByOrderByCreatedAtDesc();
    List<FeeInvoice> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<FeeInvoice> findByBatchId(String batchId);
}
