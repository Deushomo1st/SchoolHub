package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.EnrollmentCloseReq;
import com.schoolhub.schoolservice.dto.Requests.ProgressionRuleReq;
import com.schoolhub.schoolservice.model.CohortOffering;
import com.schoolhub.schoolservice.model.Enrollment;
import com.schoolhub.schoolservice.model.OfferingPrerequisite;
import com.schoolhub.schoolservice.model.ProgressionResult;
import com.schoolhub.schoolservice.model.ProgressionRule;
import com.schoolhub.schoolservice.model.Student;
import com.schoolhub.schoolservice.repository.CohortOfferingRepository;
import com.schoolhub.schoolservice.repository.EnrollmentRepository;
import com.schoolhub.schoolservice.repository.OfferingPrerequisiteRepository;
import com.schoolhub.schoolservice.repository.ProgressionResultRepository;
import com.schoolhub.schoolservice.repository.ProgressionRuleRepository;
import com.schoolhub.schoolservice.repository.SessionAttendanceRepository;
import com.schoolhub.schoolservice.repository.SessionRepository;
import com.schoolhub.schoolservice.repository.StudentRepository;
import com.schoolhub.schoolservice.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Logic for advancing. Evaluates against a fixed rule-type menu computed entirely from data
 * that already exists (Enrollment, offering_prerequisite, session_attendance) rather than a
 * custom-formula parser or an Assessment/Result rework. Failing a rule automatically flips the
 * Enrollment to "repeating" - no manual confirmation gate, matching the design's own intent.
 */
@Service
public class ProgressionRuleService {

    private record EvalOutcome(boolean passed, String detail) {}

    private final ProgressionRuleRepository repo;
    private final ProgressionResultRepository resultRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final EnrollmentService enrollmentService;
    private final CohortOfferingRepository cohortOfferingRepo;
    private final SessionRepository sessionRepo;
    private final SessionAttendanceRepository sessionAttendanceRepo;
    private final OfferingPrerequisiteRepository prereqRepo;
    private final StudentRepository studentRepo;
    private final CredentialService credentialService;
    private final AuditRecorder audit;

    public ProgressionRuleService(ProgressionRuleRepository repo, ProgressionResultRepository resultRepo,
                                 EnrollmentRepository enrollmentRepo, EnrollmentService enrollmentService,
                                 CohortOfferingRepository cohortOfferingRepo, SessionRepository sessionRepo,
                                 SessionAttendanceRepository sessionAttendanceRepo,
                                 OfferingPrerequisiteRepository prereqRepo, StudentRepository studentRepo,
                                 CredentialService credentialService, AuditRecorder audit) {
        this.repo = repo;
        this.resultRepo = resultRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.enrollmentService = enrollmentService;
        this.cohortOfferingRepo = cohortOfferingRepo;
        this.sessionRepo = sessionRepo;
        this.sessionAttendanceRepo = sessionAttendanceRepo;
        this.prereqRepo = prereqRepo;
        this.studentRepo = studentRepo;
        this.credentialService = credentialService;
        this.audit = audit;
    }

    public List<ProgressionRule> list() { return repo.findAll(); }

    public ProgressionRule get(Long id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Progression rule not found: " + id));
    }

    @Transactional
    public ProgressionRule create(ProgressionRuleReq req) {
        ProgressionRule r = new ProgressionRule();
        r.setName(req.name());
        r.setRuleType(req.ruleType());
        if (req.scopeType() != null && !req.scopeType().isBlank()) r.setScopeType(req.scopeType());
        r.setScopeRefId(req.scopeRefId());
        r.setThresholdValue(req.thresholdValue());
        r.setAutoIssueCredential(req.autoIssueCredential());
        r.setCreatedBy(TenantContext.getUserId());
        return repo.save(r);
    }

    /** MANUAL_SCORE rules need manualScore; the other two ignore it. */
    @Transactional
    public ProgressionResult evaluate(Long ruleId, Long enrollmentId, Double manualScore) {
        ProgressionRule rule = get(ruleId);
        Enrollment enrollment = enrollmentRepo.findById(enrollmentId)
                .orElseThrow(() -> new EntityNotFoundException("Enrollment not found: " + enrollmentId));

        EvalOutcome outcome = switch (rule.getRuleType()) {
            case "ATTENDANCE_MINIMUM" -> evalAttendance(rule, enrollment);
            case "PREREQUISITE_COMPLETION" -> evalPrerequisite(rule, enrollment);
            case "MANUAL_SCORE" -> evalManualScore(rule, manualScore);
            default -> throw new IllegalStateException("No evaluator for rule type " + rule.getRuleType());
        };

        ProgressionResult pr = resultRepo.findByRuleIdAndEnrollmentId(ruleId, enrollmentId).orElseGet(ProgressionResult::new);
        pr.setRuleId(ruleId);
        pr.setEnrollmentId(enrollmentId);
        pr.setPassed(outcome.passed());
        pr.setDetail(outcome.detail());
        pr.setEvaluatedBy(TenantContext.getUserId());
        pr.setEvaluatedAt(LocalDateTime.now());
        ProgressionResult saved = resultRepo.save(pr);

        audit.record("PROGRESSION_EVALUATED", rule.getName() + " -> " + (outcome.passed() ? "PASS" : "FAIL"));

        if (!outcome.passed()) {
            enrollmentService.close(enrollmentId, new EnrollmentCloseReq("repeating", "Failed progression rule: " + rule.getName()));
        } else if (rule.getAutoIssueCredential() != null) {
            studentRepo.findById(enrollment.getStudentId())
                    .map(Student::getUserId)
                    .filter(uid -> uid != null)
                    .ifPresent(uid -> credentialService.autoIssue(uid, rule.getAutoIssueCredential(), "progression_rule:" + ruleId));
        }

        return saved;
    }

    /** ATTENDANCE_MINIMUM/PREREQUISITE_COMPLETION only - MANUAL_SCORE needs a per-student score. */
    @Transactional
    public List<ProgressionResult> evaluateForCohort(Long ruleId, Long cohortId) {
        ProgressionRule rule = get(ruleId);
        if ("MANUAL_SCORE".equals(rule.getRuleType())) {
            throw new IllegalArgumentException("MANUAL_SCORE rules need a per-student score - evaluate them individually");
        }
        List<ProgressionResult> results = new ArrayList<>();
        for (Enrollment e : enrollmentRepo.findByCohortId(cohortId)) {
            if (!"active".equals(e.getStatus())) continue;
            results.add(evaluate(ruleId, e.getId(), null));
        }
        return results;
    }

    private EvalOutcome evalAttendance(ProgressionRule rule, Enrollment enrollment) {
        List<Long> cohortOfferingIds = "OFFERING".equals(rule.getScopeType())
                ? List.of(rule.getScopeRefId())
                : cohortOfferingRepo.findByCohortId(rule.getScopeRefId()).stream().map(CohortOffering::getId).toList();

        int total = 0, present = 0;
        for (Long coId : cohortOfferingIds) {
            for (var session : sessionRepo.findByCohortOfferingIdOrderByStartAtDesc(coId)) {
                var a = sessionAttendanceRepo.findBySessionIdAndStudentId(session.getId(), enrollment.getStudentId());
                if (a.isPresent()) {
                    total++;
                    if ("present".equals(a.get().getStatus())) present++;
                }
            }
        }
        double pct = total == 0 ? 0 : (present * 100.0 / total);
        double threshold = rule.getThresholdValue() == null ? 0 : rule.getThresholdValue().doubleValue();
        boolean passed = pct >= threshold;
        return new EvalOutcome(passed, String.format("attendance %.1f%% (need %.1f%%), %d/%d sessions present", pct, threshold, present, total));
    }

    private EvalOutcome evalPrerequisite(ProgressionRule rule, Enrollment enrollment) {
        List<OfferingPrerequisite> prereqs = prereqRepo.findByOfferingId(rule.getScopeRefId());
        if (prereqs.isEmpty()) return new EvalOutcome(true, "no prerequisites defined for that offering");
        List<Long> completedOfferingIds = enrollmentRepo.findByStudentIdOrderByCreatedAtDesc(enrollment.getStudentId()).stream()
                .filter(e -> "graduated".equals(e.getStatus()) && e.getOfferingId() != null)
                .map(Enrollment::getOfferingId)
                .toList();
        List<Long> missing = prereqs.stream().map(OfferingPrerequisite::getPrerequisiteId)
                .filter(id -> !completedOfferingIds.contains(id)).toList();
        boolean passed = missing.isEmpty();
        return new EvalOutcome(passed, passed ? "all prerequisites completed" : "missing prerequisite offering(s): " + missing);
    }

    private EvalOutcome evalManualScore(ProgressionRule rule, Double manualScore) {
        if (manualScore == null) throw new IllegalArgumentException("This rule type requires a manualScore");
        double threshold = rule.getThresholdValue() == null ? 0 : rule.getThresholdValue().doubleValue();
        boolean passed = manualScore >= threshold;
        return new EvalOutcome(passed, String.format("score %.1f (need %.1f)", manualScore, threshold));
    }
}
