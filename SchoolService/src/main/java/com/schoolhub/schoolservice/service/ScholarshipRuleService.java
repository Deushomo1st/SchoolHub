package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.ScholarshipRuleReq;
import com.schoolhub.schoolservice.model.CohortOffering;
import com.schoolhub.schoolservice.model.ScholarshipRule;
import com.schoolhub.schoolservice.model.Student;
import com.schoolhub.schoolservice.repository.CohortOfferingRepository;
import com.schoolhub.schoolservice.repository.FeeWaiverRepository;
import com.schoolhub.schoolservice.repository.ScholarshipRuleRepository;
import com.schoolhub.schoolservice.repository.SessionAttendanceRepository;
import com.schoolhub.schoolservice.repository.SessionRepository;
import com.schoolhub.schoolservice.repository.StudentRepository;
import com.schoolhub.schoolservice.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Automatic scholarships. Fixed rule-type menu (same philosophy as ProgressionRuleService):
 * TOP_PERFORMER reuses AcademicService's on-demand transcript average, ATTENDANCE_THRESHOLD
 * mirrors ProgressionRuleService's own attendance calculation, MANUAL_FLAG is the staff
 * escape hatch (invoking evaluate() for it at all IS the manual decision).
 */
@Service
public class ScholarshipRuleService {

    private record EvalOutcome(boolean passed, String detail) {}

    private final ScholarshipRuleRepository repo;
    private final FeeWaiverRepository waiverRepo;
    private final StudentRepository studentRepo;
    private final CohortOfferingRepository cohortOfferingRepo;
    private final SessionRepository sessionRepo;
    private final SessionAttendanceRepository sessionAttendanceRepo;
    private final AcademicService academicService;
    private final FeeService feeService;
    private final AuditRecorder audit;

    public ScholarshipRuleService(ScholarshipRuleRepository repo, FeeWaiverRepository waiverRepo,
                                  StudentRepository studentRepo, CohortOfferingRepository cohortOfferingRepo,
                                  SessionRepository sessionRepo, SessionAttendanceRepository sessionAttendanceRepo,
                                  AcademicService academicService, FeeService feeService, AuditRecorder audit) {
        this.repo = repo;
        this.waiverRepo = waiverRepo;
        this.studentRepo = studentRepo;
        this.cohortOfferingRepo = cohortOfferingRepo;
        this.sessionRepo = sessionRepo;
        this.sessionAttendanceRepo = sessionAttendanceRepo;
        this.academicService = academicService;
        this.feeService = feeService;
        this.audit = audit;
    }

    public List<ScholarshipRule> list() { return repo.findAll(); }

    public ScholarshipRule get(Long id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Scholarship rule not found: " + id));
    }

    @Transactional
    public ScholarshipRule create(ScholarshipRuleReq req) {
        ScholarshipRule r = new ScholarshipRule();
        r.setName(req.name());
        r.setRuleType(req.ruleType());
        if (req.scopeType() != null && !req.scopeType().isBlank()) r.setScopeType(req.scopeType());
        r.setScopeRefId(req.scopeRefId());
        r.setThresholdValue(req.thresholdValue());
        if (req.discountPercent() != null) r.setDiscountPercent(req.discountPercent());
        r.setCategory(req.category());
        r.setCreatedBy(TenantContext.getUserId());
        return repo.save(r);
    }

    /** Idempotent - a student already awarded this rule is reported as such, not re-waived. */
    @Transactional
    public Map<String, Object> evaluate(Long ruleId, Long studentId) {
        ScholarshipRule rule = get(ruleId);
        if (!studentRepo.existsById(studentId)) throw new EntityNotFoundException("Student not found: " + studentId);

        Map<String, Object> m = new LinkedHashMap<>();
        if (waiverRepo.existsByScholarshipRuleIdAndStudentId(ruleId, studentId)) {
            m.put("passed", true);
            m.put("alreadyAwarded", true);
            return m;
        }

        EvalOutcome outcome = switch (rule.getRuleType()) {
            case "ATTENDANCE_THRESHOLD" -> evalAttendance(rule, studentId);
            case "TOP_PERFORMER" -> evalTopPerformer(rule, studentId);
            case "MANUAL_FLAG" -> new EvalOutcome(true, "manually approved");
            default -> throw new IllegalStateException("No evaluator for rule type " + rule.getRuleType());
        };
        m.put("passed", outcome.passed());
        m.put("detail", outcome.detail());

        if (outcome.passed()) {
            Map<String, Object> award = feeService.applyWaiver(studentId, ruleId, rule.getDiscountPercent(),
                    rule.getCategory(), "Scholarship: " + rule.getName());
            m.put("invoicesWaived", award.get("invoicesWaived"));
            m.put("totalWaived", award.get("totalWaived"));
            audit.record("SCHOLARSHIP_AWARDED", rule.getName() + " -> student #" + studentId);
        }
        return m;
    }

    /** Runs every student through the rule in one batch trigger. Deliberately not a silent
     *  nightly cron - an explicit scope choice given the idempotency/dedup guard already needed
     *  per-student; a scheduled sweep can reuse this exact method later if wanted. */
    @Transactional
    public List<Map<String, Object>> evaluateAll(Long ruleId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Student s : studentRepo.findAll()) {
            Map<String, Object> row;
            try {
                row = evaluate(ruleId, s.getId());
            } catch (Exception e) {
                row = new LinkedHashMap<>();
                row.put("error", e.getMessage());
            }
            row.put("studentId", s.getId());
            out.add(row);
        }
        return out;
    }

    private EvalOutcome evalAttendance(ScholarshipRule rule, Long studentId) {
        List<Long> cohortOfferingIds = "OFFERING".equals(rule.getScopeType())
                ? List.of(rule.getScopeRefId())
                : cohortOfferingRepo.findByCohortId(rule.getScopeRefId()).stream().map(CohortOffering::getId).toList();

        int total = 0, present = 0;
        for (Long coId : cohortOfferingIds) {
            for (var session : sessionRepo.findByCohortOfferingIdOrderByStartAtDesc(coId)) {
                var a = sessionAttendanceRepo.findBySessionIdAndStudentId(session.getId(), studentId);
                if (a.isPresent()) {
                    total++;
                    if ("present".equals(a.get().getStatus())) present++;
                }
            }
        }
        double pct = total == 0 ? 0 : (present * 100.0 / total);
        double threshold = rule.getThresholdValue() == null ? 0 : rule.getThresholdValue().doubleValue();
        boolean passed = total > 0 && pct >= threshold;
        return new EvalOutcome(passed, String.format("attendance %.1f%% (need %.1f%%), %d/%d sessions present", pct, threshold, present, total));
    }

    private EvalOutcome evalTopPerformer(ScholarshipRule rule, Long studentId) {
        if (rule.getScopeRefId() == null) throw new IllegalArgumentException("TOP_PERFORMER needs a cohortOfferingId as scopeRefId");
        Map<String, Object> transcript = academicService.transcript(studentId, rule.getScopeRefId());
        Object avgObj = transcript.get("averagePercent");
        double avg = avgObj == null ? 0 : ((Number) avgObj).doubleValue();
        double threshold = rule.getThresholdValue() == null ? 0 : rule.getThresholdValue().doubleValue();
        boolean passed = avgObj != null && avg >= threshold;
        return new EvalOutcome(passed, String.format("average %.1f%% (need %.1f%%)", avg, threshold));
    }
}
