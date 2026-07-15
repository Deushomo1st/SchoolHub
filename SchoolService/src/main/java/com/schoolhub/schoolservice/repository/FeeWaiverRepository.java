package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.FeeWaiver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeeWaiverRepository extends JpaRepository<FeeWaiver, Long> {
    List<FeeWaiver> findByInvoiceId(Long invoiceId);
    boolean existsByScholarshipRuleIdAndStudentId(Long scholarshipRuleId, Long studentId);
}
