package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.EnrollmentReq;
import com.schoolhub.schoolservice.exception.ConflictException;
import com.schoolhub.schoolservice.model.Cohort;
import com.schoolhub.schoolservice.model.Guardian;
import com.schoolhub.schoolservice.model.Invite;
import com.schoolhub.schoolservice.model.InviteRedemption;
import com.schoolhub.schoolservice.model.Student;
import com.schoolhub.schoolservice.model.StudentGuardian;
import com.schoolhub.schoolservice.repository.CohortRepository;
import com.schoolhub.schoolservice.repository.GuardianRepository;
import com.schoolhub.schoolservice.repository.InviteRedemptionRepository;
import com.schoolhub.schoolservice.repository.InviteRepository;
import com.schoolhub.schoolservice.repository.StudentGuardianRepository;
import com.schoolhub.schoolservice.repository.StudentRepository;
import com.schoolhub.schoolservice.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One generalized invite/redemption mechanism (extends Tenant.staffCode's shape) covering two
 * purposes: CLASS_JOIN (code-based - a teacher hands out a code, a student redeems it, the
 * teacher confirms) and GUARDIAN_LINK (targeted - a guardian sends a request straight to the
 * ward's account by handle, no code involved, the ward decides). Both converge on the same
 * confirm/reject step; who's authorized to decide depends on the purpose (see canDecide()).
 */
@Service
public class InviteService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no I/L/O/0/1

    private final InviteRepository inviteRepo;
    private final InviteRedemptionRepository redemptionRepo;
    private final CohortRepository cohortRepo;
    private final StudentRepository studentRepo;
    private final GuardianRepository guardianRepo;
    private final StudentGuardianRepository linkRepo;
    private final EnrollmentService enrollmentService;
    private final NotificationService notifications;
    private final AuditRecorder audit;

    public InviteService(InviteRepository inviteRepo, InviteRedemptionRepository redemptionRepo,
                        CohortRepository cohortRepo, StudentRepository studentRepo,
                        GuardianRepository guardianRepo, StudentGuardianRepository linkRepo,
                        EnrollmentService enrollmentService, NotificationService notifications,
                        AuditRecorder audit) {
        this.inviteRepo = inviteRepo;
        this.redemptionRepo = redemptionRepo;
        this.cohortRepo = cohortRepo;
        this.studentRepo = studentRepo;
        this.guardianRepo = guardianRepo;
        this.linkRepo = linkRepo;
        this.enrollmentService = enrollmentService;
        this.notifications = notifications;
        this.audit = audit;
    }

    // ---- Class-join (code-based) ----

    @Transactional
    public Invite issueClassJoin(Long cohortId, Integer maxRedemptions) {
        if (!cohortRepo.existsById(cohortId)) throw new EntityNotFoundException("Cohort not found: " + cohortId);
        Invite inv = new Invite();
        inv.setTenantId(TenantContext.getTenantId());
        inv.setIssuerUserId(TenantContext.getUserId());
        inv.setPurpose("CLASS_JOIN");
        inv.setScopeType("COHORT");
        inv.setScopeRefId(cohortId);
        inv.setCode(randomCode());
        inv.setMaxRedemptions(maxRedemptions != null ? maxRedemptions : 100);
        Invite saved = inviteRepo.save(inv);
        audit.record("CLASS_JOIN_ISSUED", "cohort #" + cohortId);
        return saved;
    }

    @Transactional
    public InviteRedemption redeemClassJoin(String code) {
        Invite inv = inviteRepo.findByCode(code == null ? "" : code.trim().toUpperCase())
                .orElseThrow(() -> new EntityNotFoundException("Invalid code"));
        if (!"active".equals(inv.getStatus())) throw new IllegalArgumentException("That code is no longer active");
        if (inv.getRedemptionCount() >= inv.getMaxRedemptions()) {
            throw new IllegalArgumentException("That code has reached its redemption limit");
        }
        if (inv.getExpiresAt() != null && inv.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("That code has expired");
        }

        InviteRedemption r = new InviteRedemption();
        r.setInviteId(inv.getId());
        r.setRedeemedByUserId(TenantContext.getUserId());
        InviteRedemption saved = redemptionRepo.save(r);

        inv.setRedemptionCount(inv.getRedemptionCount() + 1);
        if (inv.getRedemptionCount() >= inv.getMaxRedemptions()) inv.setStatus("redeemed");
        inviteRepo.save(inv);

        audit.record("CLASS_JOIN_REDEEMED", "invite #" + inv.getId());
        notifications.notify(inv.getIssuerUserId(), "INVITE_REDEEMED", "Someone joined with your class code",
                "Awaiting your confirmation", "INVITE_REDEMPTION", saved.getId());
        return saved;
    }

    // ---- Guardian link (targeted - the redeemer is already known, no code needed) ----

    @Transactional
    public InviteRedemption linkGuardianToWard(Long wardUserId) {
        Long me = TenantContext.getUserId();
        guardianRepo.findByUserId(me).orElseThrow(() -> new AccessDeniedException("Only a guardian can send a link request"));
        Student ward = studentRepo.findByUserId(wardUserId)
                .orElseThrow(() -> new EntityNotFoundException("No student account with that handle"));
        Guardian g = guardianRepo.findByUserId(me).orElseThrow();
        if (linkRepo.findByGuardianId(g.getId()).stream().anyMatch(l -> l.getStudentId().equals(ward.getId()))) {
            throw new ConflictException("You are already linked to that student");
        }

        Invite inv = new Invite();
        inv.setTenantId(TenantContext.getTenantId());
        inv.setIssuerUserId(me);
        inv.setPurpose("GUARDIAN_LINK");
        inv.setTargetUserId(wardUserId);
        inv.setMaxRedemptions(1);
        Invite savedInvite = inviteRepo.save(inv);

        InviteRedemption r = new InviteRedemption();
        r.setInviteId(savedInvite.getId());
        r.setRedeemedByUserId(wardUserId);
        InviteRedemption saved = redemptionRepo.save(r);

        audit.record("GUARDIAN_LINK_REQUESTED", "ward user #" + wardUserId);
        notifications.notify(wardUserId, "GUARDIAN_LINK_REQUESTED", "A guardian wants to link to your account",
                "Review and accept/reject", "INVITE_REDEMPTION", saved.getId());
        return saved;
    }

    // ---- Shared confirm/reject, dispatched by purpose ----

    @Transactional
    public Object confirm(Long redemptionId) {
        InviteRedemption r = getRedemption(redemptionId);
        Invite inv = getInvite(r.getInviteId());
        requireCanDecide(inv, r);
        if (!"pending_confirmation".equals(r.getStatus())) {
            throw new IllegalArgumentException("That redemption is not awaiting confirmation");
        }
        Object applied = switch (inv.getPurpose()) {
            case "CLASS_JOIN" -> applyClassJoin(inv, r);
            case "GUARDIAN_LINK" -> applyGuardianLink(inv, r);
            default -> throw new IllegalStateException("No applier for invite purpose " + inv.getPurpose());
        };
        r.setStatus("confirmed");
        r.setConfirmedByUserId(TenantContext.getUserId());
        r.setDecidedAt(LocalDateTime.now());
        redemptionRepo.save(r);
        audit.record("INVITE_CONFIRMED", inv.getPurpose() + " #" + inv.getId());
        return applied;
    }

    @Transactional
    public void reject(Long redemptionId) {
        InviteRedemption r = getRedemption(redemptionId);
        Invite inv = getInvite(r.getInviteId());
        requireCanDecide(inv, r);
        if (!"pending_confirmation".equals(r.getStatus())) {
            throw new IllegalArgumentException("That redemption is not awaiting confirmation");
        }
        r.setStatus("rejected");
        r.setConfirmedByUserId(TenantContext.getUserId());
        r.setDecidedAt(LocalDateTime.now());
        redemptionRepo.save(r);
        audit.record("INVITE_REJECTED", inv.getPurpose() + " #" + inv.getId());
    }

    /** Redemptions awaiting a decision this caller is authorized to make. */
    public List<Map<String, Object>> incoming() {
        Long me = TenantContext.getUserId();
        List<Map<String, Object>> out = new ArrayList<>();
        for (InviteRedemption r : redemptionRepo.findByStatus("pending_confirmation")) {
            Invite inv = inviteRepo.findById(r.getInviteId()).orElse(null);
            if (inv == null || !inv.getTenantId().equals(TenantContext.getTenantId())) continue;
            if (canDecide(inv, r, me)) out.add(row(inv, r));
        }
        return out;
    }

    private Object applyClassJoin(Invite inv, InviteRedemption r) {
        Student s = studentRepo.findByUserId(r.getRedeemedByUserId())
                .orElseThrow(() -> new EntityNotFoundException("No student profile linked to that account"));
        Cohort cohort = cohortRepo.findById(inv.getScopeRefId())
                .orElseThrow(() -> new EntityNotFoundException("Cohort not found: " + inv.getScopeRefId()));
        return enrollmentService.create(new EnrollmentReq(s.getId(), cohort.getId(), null, null, cohort.getSchedulePeriodId()));
    }

    private Object applyGuardianLink(Invite inv, InviteRedemption r) {
        Guardian g = guardianRepo.findByUserId(inv.getIssuerUserId())
                .orElseThrow(() -> new EntityNotFoundException("No guardian profile linked to that account"));
        Student ward = studentRepo.findByUserId(inv.getTargetUserId())
                .orElseThrow(() -> new EntityNotFoundException("No student profile linked to that account"));
        StudentGuardian link = new StudentGuardian();
        link.setStudentId(ward.getId());
        link.setGuardianId(g.getId());
        link.setRelationship("Guardian");
        return linkRepo.save(link);
    }

    private void requireCanDecide(Invite inv, InviteRedemption r) {
        if (!canDecide(inv, r, TenantContext.getUserId())) throw new AccessDeniedException("You cannot decide on that request");
    }

    /** CLASS_JOIN: the issuer (teacher) decides. GUARDIAN_LINK: the target (ward) decides. */
    private boolean canDecide(Invite inv, InviteRedemption r, Long callerId) {
        return switch (inv.getPurpose()) {
            case "CLASS_JOIN" -> inv.getIssuerUserId().equals(callerId);
            case "GUARDIAN_LINK" -> r.getRedeemedByUserId().equals(callerId);
            default -> false;
        };
    }

    private Invite getInvite(Long id) {
        return inviteRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Invite not found: " + id));
    }

    private InviteRedemption getRedemption(Long id) {
        return redemptionRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Redemption not found: " + id));
    }

    private Map<String, Object> row(Invite inv, InviteRedemption r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("redemptionId", r.getId());
        m.put("inviteId", inv.getId());
        m.put("purpose", inv.getPurpose());
        m.put("redeemedByUserId", r.getRedeemedByUserId());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    private String randomCode() {
        SecureRandom rnd = new SecureRandom();
        String candidate;
        do {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) sb.append(CODE_ALPHABET.charAt(rnd.nextInt(CODE_ALPHABET.length())));
            candidate = sb.toString();
        } while (inviteRepo.findByCode(candidate).isPresent());
        return candidate;
    }
}
