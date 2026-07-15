package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.InvoiceReq;
import com.schoolhub.schoolservice.dto.Requests.PaymentReq;
import com.schoolhub.schoolservice.dto.Requests.RefundReq;
import com.schoolhub.schoolservice.dto.Requests.ResourcePostReq;
import com.schoolhub.schoolservice.model.*;
import com.schoolhub.schoolservice.payment.PaymentGateway;
import com.schoolhub.schoolservice.payment.PaymentProvider;
import com.schoolhub.schoolservice.tenant.TenantContext;
import com.schoolhub.schoolservice.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** School fees: invoices and payments (Bursar). Online pay is a simulated Paystack charge. */
@Service
public class FeeService {

    private final FeeInvoiceRepository invoiceRepo;
    private final FeePaymentRepository paymentRepo;
    private final FeeWaiverRepository waiverRepo;
    private final StudentRepository studentRepo;
    private final GuardianRepository guardianRepo;
    private final StudentGuardianRepository linkRepo;
    private final FeeCategoryService feeCategoryService;
    private final FinancialSettingsService financialSettings;
    private final PaymentGateway paymentGateway;
    private final AuditRecorder audit;

    public FeeService(FeeInvoiceRepository invoiceRepo, FeePaymentRepository paymentRepo, FeeWaiverRepository waiverRepo,
                      StudentRepository studentRepo, GuardianRepository guardianRepo, StudentGuardianRepository linkRepo,
                      FeeCategoryService feeCategoryService, FinancialSettingsService financialSettings,
                      PaymentGateway paymentGateway, AuditRecorder audit) {
        this.invoiceRepo = invoiceRepo;
        this.paymentRepo = paymentRepo;
        this.waiverRepo = waiverRepo;
        this.studentRepo = studentRepo;
        this.guardianRepo = guardianRepo;
        this.linkRepo = linkRepo;
        this.feeCategoryService = feeCategoryService;
        this.financialSettings = financialSettings;
        this.paymentGateway = paymentGateway;
        this.audit = audit;
    }

    public List<Map<String, Object>> listInvoices() {
        Map<Long, String> names = studentNames();
        return invoiceRepo.findAllByOrderByCreatedAtDesc().stream().map(i -> row(i, names)).collect(Collectors.toList());
    }

    public List<Map<String, Object>> invoicesForStudent(Long studentId) {
        Map<Long, String> names = studentNames();
        return invoiceRepo.findByStudentIdOrderByCreatedAtDesc(studentId).stream().map(i -> row(i, names)).collect(Collectors.toList());
    }

    public Map<String, Object> summary() {
        List<FeeInvoice> all = invoiceRepo.findAll();
        int billed = all.stream().filter(i -> !"cancelled".equals(i.getStatus())).mapToInt(FeeInvoice::getAmountNaira).sum();
        int collected = paymentRepo.findAll().stream().mapToInt(FeePayment::getAmountNaira).sum();
        long unpaid = all.stream().filter(i -> !"paid".equals(i.getStatus()) && !"cancelled".equals(i.getStatus())).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("billed", billed);
        m.put("collected", collected);
        m.put("outstanding", Math.max(0, billed - collected));
        m.put("invoices", all.size());
        m.put("unpaid", unpaid);
        m.put("currencyCode", financialSettings.get().getCurrencyCode());
        m.put("currencySymbol", financialSettings.get().getCurrencySymbol());
        return m;
    }

    @Transactional
    public FeeInvoice createInvoice(InvoiceReq req) {
        if (!studentRepo.existsById(req.studentId())) {
            throw new EntityNotFoundException("Student not found: " + req.studentId());
        }
        if (req.amountNaira() == null || req.amountNaira() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        FeeInvoice i = new FeeInvoice();
        i.setStudentId(req.studentId());
        i.setTitle(req.title());
        if (req.term() != null && !req.term().isBlank()) i.setTerm(req.term());
        i.setAmountNaira(req.amountNaira());
        i.setDueDate(req.dueDate());
        i.setStatus("unpaid");
        i.setCreatedBy(TenantContext.getUserId());
        FeeInvoice saved = invoiceRepo.save(i);
        audit.record("INVOICE_ISSUED", currencySymbol() + saved.getAmountNaira() + " - " + saved.getTitle() + " (student #" + saved.getStudentId() + ")");
        return saved;
    }

    @Transactional
    public FeePayment recordPayment(PaymentReq req) {
        FeeInvoice inv = invoiceRepo.findById(req.invoiceId())
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));
        String method = req.method() == null ? "cash" : req.method().toLowerCase();
        if (!Set.of("cash", "transfer").contains(method)) {
            throw new IllegalArgumentException("Manual payment method must be cash or transfer");
        }
        if (req.amountNaira() == null || req.amountNaira() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        FeePayment p = savePayment(inv, req.amountNaira(), method, req.reference());
        recompute(inv);
        audit.record("PAYMENT_RECORDED", currencySymbol() + req.amountNaira() + " (" + method + ") on invoice #" + inv.getId());
        return p;
    }

    /** Refund is a negative fee_payment row of kind='refund' - paidFor()'s existing sum nets it
     *  out for free, so outstanding()/recompute() need no separate refund-aware branch. A
     *  provider-settled payment is reversed through the PORT first; a manual cash/transfer entry
     *  has no provider to call back, so it's a straight bookkeeping reversal. */
    @Transactional
    public FeePayment refund(RefundReq req) {
        FeePayment original = paymentRepo.findById(req.paymentId())
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + req.paymentId()));
        if ("refund".equals(original.getKind())) throw new IllegalArgumentException("Cannot refund a refund");
        if (req.amountNaira() == null || req.amountNaira() <= 0) throw new IllegalArgumentException("Amount must be greater than zero");
        if (req.amountNaira() > original.getAmountNaira()) throw new IllegalArgumentException("Cannot refund more than the original payment");
        FeeInvoice inv = invoiceRepo.findById(original.getInvoiceId())
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));

        String refundRef = "REFUND-" + original.getId();
        if (original.getMethod().equalsIgnoreCase(paymentGateway.active().key())) {
            var result = paymentGateway.active().refund(original.getReference(), req.amountNaira());
            if (!result.success()) throw new IllegalStateException("Provider declined the refund");
        }

        FeePayment refund = new FeePayment();
        refund.setInvoiceId(inv.getId());
        refund.setAmountNaira(-req.amountNaira());
        refund.setMethod(original.getMethod());
        refund.setReference(refundRef);
        refund.setKind("refund");
        refund.setRecordedBy(TenantContext.getUserId());
        FeePayment saved = paymentRepo.save(refund);
        recompute(inv);
        audit.record("PAYMENT_REFUNDED", currencySymbol() + req.amountNaira() + " on invoice #" + inv.getId());
        return saved;
    }

    /** A student or one of their guardians settles an invoice online, via the payment PORT
     *  (initialize -> server-side verify -> record - the browser's word alone is never trusted). */
    @Transactional
    public Map<String, Object> payOnline(Long invoiceId) {
        FeeInvoice inv = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found"));
        assertCallerOwns(inv.getStudentId());
        int outstanding = outstanding(inv);
        if (outstanding <= 0) throw new IllegalArgumentException("This invoice is already settled");

        PaymentProvider provider = paymentGateway.active();
        var init = provider.initialize(outstanding, inv.getTitle());
        var verify = provider.verify(init.reference());
        if (!verify.success()) throw new IllegalStateException("Payment could not be verified");

        savePayment(inv, outstanding, provider.key(), verify.reference());
        recompute(inv);
        audit.record("PAYMENT_ONLINE", currencySymbol() + outstanding + " (" + provider.key() + " " + verify.reference() + ") on invoice #" + inv.getId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", "Payment successful via " + provider.key());
        m.put("reference", verify.reference());
        m.put("amount", outstanding);
        return m;
    }

    // ---- Resource point: school posts a payable item to many students at once ----

    /**
     * @param autoApprove true for an ADMIN/PRINCIPAL (item goes live); false for other staff
     *                    (e.g. Bursar) - the item is a 'draft' that the school admin must approve.
     */
    @Transactional
    public Map<String, Object> postResource(ResourcePostReq req, boolean autoApprove) {
        String category = req.category() == null ? "" : req.category().toLowerCase();
        if (!feeCategoryService.isValid(category)) {
            throw new IllegalArgumentException("Unknown fee category: " + category);
        }
        if (req.amountNaira() == null || req.amountNaira() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        List<Student> targets = resolveAudience(req.audienceType(), req.audienceId());
        if (targets.isEmpty()) throw new IllegalArgumentException("No students match that audience");

        String status = autoApprove ? "unpaid" : "draft";
        String batchId = UUID.randomUUID().toString();
        boolean compulsory = req.compulsory() == null || req.compulsory();
        for (Student s : targets) {
            FeeInvoice i = new FeeInvoice();
            i.setStudentId(s.getId());
            i.setTitle(req.title());
            if (req.term() != null && !req.term().isBlank()) i.setTerm(req.term());
            i.setCategory(category);
            i.setCompulsory(compulsory);
            i.setCoverImageUrl(blankToNull(req.coverImageUrl()));
            i.setDescription(blankToNull(req.description()));
            i.setBatchId(batchId);
            i.setAmountNaira(req.amountNaira());
            i.setDueDate(req.dueDate());
            i.setStatus(status);
            i.setCreatedBy(TenantContext.getUserId());
            invoiceRepo.save(i);
        }
        audit.record(autoApprove ? "RESOURCE_POSTED" : "RESOURCE_DRAFTED",
                category + " '" + req.title() + "' " + currencySymbol() + req.amountNaira() + " to " + targets.size() + " student(s)");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("batchId", batchId);
        m.put("title", req.title());
        m.put("students", targets.size());
        m.put("status", status);
        m.put("message", autoApprove
                ? "Posted to " + targets.size() + " student(s)."
                : "Submitted to the school admin for approval.");
        return m;
    }

    /** Admin approves a staff-drafted item: every invoice in the batch goes live (draft -> unpaid). */
    @Transactional
    public Map<String, Object> approveBatch(String batchId) {
        List<FeeInvoice> rows = invoiceRepo.findByBatchId(batchId);
        if (rows.isEmpty()) throw new EntityNotFoundException("No such payment to approve");
        int approved = 0;
        for (FeeInvoice i : rows) {
            if ("draft".equals(i.getStatus())) { i.setStatus("unpaid"); invoiceRepo.save(i); approved++; }
        }
        if (approved == 0) throw new IllegalArgumentException("That payment is not awaiting approval");
        audit.record("RESOURCE_APPROVED", rows.get(0).getTitle() + " (" + approved + " student(s))");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("batchId", batchId);
        m.put("approved", approved);
        return m;
    }

    /** Admin rejects a staff-drafted item: the whole draft batch is removed (never reached students). */
    @Transactional
    public void rejectBatch(String batchId) {
        List<FeeInvoice> rows = invoiceRepo.findByBatchId(batchId);
        if (rows.isEmpty()) throw new EntityNotFoundException("No such payment to reject");
        if (rows.stream().anyMatch(i -> !"draft".equals(i.getStatus()))) {
            throw new IllegalArgumentException("That payment is already live and cannot be rejected here");
        }
        String title = rows.get(0).getTitle();
        invoiceRepo.deleteAll(rows);
        audit.record("RESOURCE_REJECTED", title);
    }

    /** The school's view of what it has posted: one row per posted item (grouped by batch). */
    public List<Map<String, Object>> listResourceItems() {
        Map<String, List<FeeInvoice>> byBatch = invoiceRepo.findAll().stream()
                .filter(i -> i.getBatchId() != null)
                .collect(Collectors.groupingBy(FeeInvoice::getBatchId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (List<FeeInvoice> rows : byBatch.values()) {
            FeeInvoice first = rows.get(0);
            int collected = rows.stream().mapToInt(i -> paidFor(i.getId())).sum();
            long paidCount = rows.stream().filter(i -> "paid".equals(i.getStatus())).count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("batchId", first.getBatchId());
            m.put("category", first.getCategory());
            m.put("title", first.getTitle());
            m.put("description", first.getDescription());
            m.put("coverImageUrl", first.getCoverImageUrl());
            m.put("amount", first.getAmountNaira());
            m.put("compulsory", first.isCompulsory());
            m.put("dueDate", first.getDueDate());
            m.put("status", first.getStatus());            // 'draft' = awaiting approval (slice 5), else live
            m.put("students", rows.size());
            m.put("paidCount", paidCount);
            m.put("collected", collected);
            m.put("createdAt", first.getCreatedAt());
            out.add(m);
        }
        out.sort(Comparator.comparing((Map<String, Object> m) -> (java.time.LocalDateTime) m.get("createdAt")).reversed());
        return out;
    }

    /** "For You": every obligation the signed-in student (or a guardian's children) owes. */
    public Map<String, Object> forYou() {
        Long uid = TenantContext.getUserId();
        if (uid == null) throw new AccessDeniedException("Not signed in");
        Map<Long, String> names = studentNames();

        List<Long> studentIds = new ArrayList<>();
        Student self = studentRepo.findByUserId(uid).orElse(null);
        if (self != null) {
            studentIds.add(self.getId());
        } else {
            Guardian g = guardianRepo.findByUserId(uid).orElse(null);
            if (g != null) {
                for (StudentGuardian l : linkRepo.findByGuardianId(g.getId())) studentIds.add(l.getStudentId());
            }
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (Long sid : studentIds) {
            for (FeeInvoice i : invoiceRepo.findByStudentIdOrderByCreatedAtDesc(sid)) {
                // students never see staff drafts (awaiting approval) or cancelled items
                if (!"draft".equals(i.getStatus()) && !"cancelled".equals(i.getStatus())) items.add(row(i, names));
            }
        }
        int outstanding = items.stream().mapToInt(m -> (int) m.get("outstanding")).sum();
        int compulsoryOutstanding = items.stream()
                .filter(m -> Boolean.TRUE.equals(m.get("compulsory")))
                .mapToInt(m -> (int) m.get("outstanding")).sum();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("totalOutstanding", outstanding);
        out.put("compulsoryOutstanding", compulsoryOutstanding);
        out.put("multiChild", studentIds.size() > 1);
        return out;
    }

    private List<Student> resolveAudience(String type, Long id) {
        String t = type == null ? "" : type.toUpperCase();
        switch (t) {
            case "ALL":
                return studentRepo.findAll();
            case "CLASS":
                if (id == null) throw new IllegalArgumentException("Choose a class");
                return studentRepo.findAll().stream().filter(s -> id.equals(s.getClassId())).collect(Collectors.toList());
            case "STUDENT":
                if (id == null) throw new IllegalArgumentException("Choose a student");
                return List.of(studentRepo.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Student not found: " + id)));
            default:
                throw new IllegalArgumentException("audienceType must be ALL, CLASS or STUDENT");
        }
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s; }

    // ---- helpers ----
    private FeePayment savePayment(FeeInvoice inv, int amount, String method, String ref) {
        FeePayment p = new FeePayment();
        p.setInvoiceId(inv.getId());
        p.setAmountNaira(amount);
        p.setMethod(method);
        p.setReference(ref);
        p.setRecordedBy(TenantContext.getUserId());
        return paymentRepo.save(p);
    }

    private int paidFor(Long invoiceId) {
        return paymentRepo.findByInvoiceId(invoiceId).stream().mapToInt(FeePayment::getAmountNaira).sum();
    }

    private int waivedFor(Long invoiceId) {
        return waiverRepo.findByInvoiceId(invoiceId).stream().mapToInt(FeeWaiver::getAmountNaira).sum();
    }

    private int outstanding(FeeInvoice i) {
        return Math.max(0, i.getAmountNaira() - paidFor(i.getId()) - waivedFor(i.getId()));
    }

    private void recompute(FeeInvoice i) {
        if ("cancelled".equals(i.getStatus())) return;
        int covered = paidFor(i.getId()) + waivedFor(i.getId());
        i.setStatus(covered >= i.getAmountNaira() ? "paid" : (covered > 0 ? "partial" : "unpaid"));
        invoiceRepo.save(i);
    }

    private String currencySymbol() { return financialSettings.get().getCurrencySymbol(); }

    /** Called by ScholarshipRuleService once a rule passes. Spreads the discount across the
     *  student's current outstanding invoices (optionally filtered to one category) - future
     *  invoices aren't auto-covered, a fresh evaluate() run is needed for those. */
    @Transactional
    public Map<String, Object> applyWaiver(Long studentId, Long scholarshipRuleId, BigDecimal discountPercent,
                                           String categoryFilter, String reason) {
        List<FeeInvoice> candidates = invoiceRepo.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .filter(i -> Set.of("unpaid", "partial").contains(i.getStatus()))
                .filter(i -> categoryFilter == null || categoryFilter.isBlank() || categoryFilter.equalsIgnoreCase(i.getCategory()))
                .toList();
        BigDecimal pct = discountPercent == null ? BigDecimal.valueOf(100) : discountPercent;
        int count = 0, total = 0;
        for (FeeInvoice i : candidates) {
            int outstandingAmt = outstanding(i);
            if (outstandingAmt <= 0) continue;
            int waiverAmount = pct.multiply(BigDecimal.valueOf(outstandingAmt))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).intValue();
            if (waiverAmount <= 0) continue;
            FeeWaiver w = new FeeWaiver();
            w.setInvoiceId(i.getId());
            w.setStudentId(studentId);
            w.setScholarshipRuleId(scholarshipRuleId);
            w.setAmountNaira(waiverAmount);
            w.setReason(reason);
            w.setCreatedBy(TenantContext.getUserId());
            waiverRepo.save(w);
            recompute(i);
            count++;
            total += waiverAmount;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("invoicesWaived", count);
        m.put("totalWaived", total);
        return m;
    }

    /** Splits an invoice's remaining balance into N new invoices spaced intervalDays apart
     *  (last one absorbs the rounding remainder), and marks the original superseded. Called
     *  directly by ADMIN/BURSAR or via WorkflowService's INSTALLMENT_REQUEST applier. */
    @Transactional
    public Map<String, Object> splitIntoInstallments(Long invoiceId, int installments, int intervalDays) {
        FeeInvoice original = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + invoiceId));
        if ("cancelled".equals(original.getStatus()) || "paid".equals(original.getStatus())) {
            throw new IllegalArgumentException("Only an unpaid or partially paid invoice can be split into installments");
        }
        int remaining = outstanding(original);
        if (remaining <= 0) throw new IllegalArgumentException("This invoice has no outstanding balance to split");

        String batchId = UUID.randomUUID().toString();
        int base = remaining / installments;
        int remainder = remaining % installments;
        LocalDate due = original.getDueDate() != null ? original.getDueDate() : LocalDate.now();
        List<Long> createdIds = new ArrayList<>();
        for (int idx = 0; idx < installments; idx++) {
            int amount = base + (idx == installments - 1 ? remainder : 0);
            FeeInvoice part = new FeeInvoice();
            part.setStudentId(original.getStudentId());
            part.setTitle(original.getTitle() + " (Installment " + (idx + 1) + "/" + installments + ")");
            part.setTerm(original.getTerm());
            part.setCategory(original.getCategory());
            part.setCompulsory(original.isCompulsory());
            part.setBatchId(batchId);
            part.setAmountNaira(amount);
            part.setDueDate(due.plusDays((long) intervalDays * idx));
            part.setStatus("unpaid");
            part.setCreatedBy(TenantContext.getUserId());
            createdIds.add(invoiceRepo.save(part).getId());
        }
        original.setStatus("cancelled");
        invoiceRepo.save(original);
        audit.record("INVOICE_SPLIT_INTO_INSTALLMENTS", "invoice #" + invoiceId + " -> " + installments + " parts");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("batchId", batchId);
        m.put("originalInvoiceId", invoiceId);
        m.put("installmentInvoiceIds", createdIds);
        m.put("amountPerInstallment", base);
        return m;
    }

    /** WorkflowService calls this before creating a pending_confirmation installment request -
     *  a student/guardian may only request installments on their own invoice. */
    public void assertCallerOwnsInvoice(Long invoiceId) {
        FeeInvoice inv = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + invoiceId));
        assertCallerOwns(inv.getStudentId());
    }

    private Map<String, Object> row(FeeInvoice i, Map<Long, String> names) {
        int paid = paidFor(i.getId());
        int waived = waivedFor(i.getId());
        int outstandingAmt = outstanding(i);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("studentId", i.getStudentId());
        m.put("student", names.getOrDefault(i.getStudentId(), "?"));
        m.put("title", i.getTitle());
        m.put("term", i.getTerm());
        m.put("category", i.getCategory());
        m.put("compulsory", i.isCompulsory());
        m.put("coverImageUrl", i.getCoverImageUrl());
        m.put("description", i.getDescription());
        m.put("amount", i.getAmountNaira());
        m.put("paid", paid);
        m.put("waived", waived);
        m.put("outstanding", outstandingAmt);
        m.put("status", i.getStatus());
        m.put("dueDate", i.getDueDate());
        // For the "red-ticking" deadline: days left (null if no due date), and overdue only when money is still owed.
        LocalDate due = i.getDueDate();
        m.put("daysLeft", due == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), due));
        m.put("overdue", due != null && outstandingAmt > 0 && due.isBefore(LocalDate.now()));
        return m;
    }

    private Map<Long, String> studentNames() {
        Map<Long, String> m = new HashMap<>();
        for (Student s : studentRepo.findAll()) m.put(s.getId(), s.getLastName() + ", " + s.getFirstName());
        return m;
    }

    private void assertCallerOwns(Long studentId) {
        Long uid = TenantContext.getUserId();
        if (uid == null) throw new AccessDeniedException("Not signed in");
        Student self = studentRepo.findByUserId(uid).orElse(null);
        if (self != null && self.getId().equals(studentId)) return;
        Guardian g = guardianRepo.findByUserId(uid).orElse(null);
        if (g != null && linkRepo.findByGuardianId(g.getId()).stream().anyMatch(l -> l.getStudentId().equals(studentId))) return;
        throw new AccessDeniedException("You can only pay your own fees");
    }
}
