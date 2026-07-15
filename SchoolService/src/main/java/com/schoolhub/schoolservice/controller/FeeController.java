package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.InstallmentRequestReq;
import com.schoolhub.schoolservice.dto.Requests.InvoiceReq;
import com.schoolhub.schoolservice.dto.Requests.PaymentReq;
import com.schoolhub.schoolservice.dto.Requests.RefundReq;
import com.schoolhub.schoolservice.dto.Requests.ResourcePostReq;
import com.schoolhub.schoolservice.service.FeeService;
import com.schoolhub.schoolservice.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class FeeController {

    private final FeeService fees;
    private final WorkflowService workflow;

    public FeeController(FeeService fees, WorkflowService workflow) {
        this.fees = fees;
        this.workflow = workflow;
    }

    // ---- Bursar / admin: manage invoices + record manual payments ----
    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN','BURSAR')")
    public ResponseEntity<?> invoices() {
        return ResponseEntity.ok(fees.listInvoices());
    }

    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN','BURSAR')")
    public ResponseEntity<?> createInvoice(@Valid @RequestBody InvoiceReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fees.createInvoice(req));
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN','BURSAR')")
    public ResponseEntity<?> recordPayment(@Valid @RequestBody PaymentReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fees.recordPayment(req));
    }

    @GetMapping("/fees/summary")
    @PreAuthorize("hasAnyRole('ADMIN','BURSAR')")
    public ResponseEntity<?> summary() {
        return ResponseEntity.ok(fees.summary());
    }

    // ---- Resource point: school posts a payable item (book/participation/other/fee) to many students ----
    // ADMIN/PRINCIPAL posts go live; other staff (Bursar) create a draft the admin must approve.
    @PostMapping("/payments/items")
    @PreAuthorize("hasAnyRole('ADMIN','BURSAR')")
    public ResponseEntity<?> postResource(@Valid @RequestBody ResourcePostReq req, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));   // PRINCIPAL is granted ROLE_ADMIN too
        return ResponseEntity.status(HttpStatus.CREATED).body(fees.postResource(req, isAdmin));
    }

    @GetMapping("/payments/items")
    @PreAuthorize("hasAnyRole('ADMIN','BURSAR')")
    public ResponseEntity<?> resourceItems() {
        return ResponseEntity.ok(fees.listResourceItems());
    }

    // ---- School admin approves / rejects a staff-drafted item ----
    @PostMapping("/payments/items/{batchId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> approveResource(@PathVariable String batchId) {
        return ResponseEntity.ok(fees.approveBatch(batchId));
    }

    @PostMapping("/payments/items/{batchId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rejectResource(@PathVariable String batchId) {
        fees.rejectBatch(batchId);
        return ResponseEntity.ok().build();
    }

    // ---- Student / guardian: settle own invoice online (ownership enforced in service) ----
    @PostMapping("/invoices/{id}/pay")
    public ResponseEntity<?> payOnline(@PathVariable Long id) {
        return ResponseEntity.ok(fees.payOnline(id));
    }

    // ---- Bursar / admin: refund a recorded payment (a negative fee_payment row) ----
    @PostMapping("/payments/refund")
    @PreAuthorize("hasAnyRole('ADMIN','BURSAR')")
    public ResponseEntity<?> refund(@Valid @RequestBody RefundReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fees.refund(req));
    }

    // ---- Split an invoice into installments: standing permission for Admin/Bursar, otherwise
    // routed through WorkflowService as a pending_confirmation request (ownership enforced there) ----
    @PostMapping("/invoices/{id}/installment-request")
    public ResponseEntity<?> requestInstallments(@PathVariable Long id, @Valid @RequestBody InstallmentRequestReq req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(workflow.initiateInstallmentRequest(id, req.installments(), req.intervalDays(), req.reason()));
    }
}
