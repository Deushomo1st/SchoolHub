package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.FeePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeePaymentRepository extends JpaRepository<FeePayment, Long> {
    List<FeePayment> findByInvoiceId(Long invoiceId);
    List<FeePayment> findAllByOrderByCreatedAtDesc();
}
