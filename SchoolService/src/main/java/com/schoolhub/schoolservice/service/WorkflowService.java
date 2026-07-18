package com.schoolhub.schoolservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolhub.schoolservice.dto.Requests.GuardianClaimReq;
import com.schoolhub.schoolservice.dto.Requests.OfferingReq;
import com.schoolhub.schoolservice.dto.Requests.OrgUnitReq;
import com.schoolhub.schoolservice.dto.Requests.ProgressionRuleReq;
import com.schoolhub.schoolservice.model.OrgUnit;
import com.schoolhub.schoolservice.model.Role;
import com.schoolhub.schoolservice.model.RoleAssignment;
import com.schoolhub.schoolservice.model.WorkflowProtest;
import com.schoolhub.schoolservice.model.WorkflowRequest;
import com.schoolhub.schoolservice.repository.AppUserRepository;
import com.schoolhub.schoolservice.repository.RoleAssignmentRepository;
import com.schoolhub.schoolservice.repository.RoleRepository;
import com.schoolhub.schoolservice.repository.WorkflowProtestRepository;
import com.schoolhub.schoolservice.repository.WorkflowRequestRepository;
import com.schoolhub.schoolservice.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one generalized propose/confirm/protest engine, replacing what would otherwise be a
 * bespoke table+state-machine per feature. Two shapes coexist:
 *  - "NONE" protest style (ORG_UNIT_CREATE, OFFERING_CREATE): a non-Admin proposes, an Admin
 *    confirms/rejects. An Admin proposing directly has standing permission - it applies at once,
 *    still logging a workflow_request row (already 'applied') for audit/history.
 *  - "FORMAL" protest style (ORG_UNIT_DELETE): the action's *tag* takes effect immediately (the
 *    org unit flips to pending_deletion right away, visible everywhere) but the actual deletion
 *    sits behind a 7-day protest window. Anyone in the branch can raise a protest; it has no
 *    binding effect by itself - only a moderator seconding (cancels) or dismissing (clears the
 *    way) it moves things forward.
 */
@Service
public class WorkflowService {

    private record DeletePayload(Long orgUnitId) {}
    private record GuardianLinkPayload(Long guardianId, Long studentId, String relationship) {}
    private record CredentialRevokePayload(Long credentialId, String reason) {}
    private record ResultDisputePayload(Long resultId, String reason) {}
    private record InstallmentPayload(Long invoiceId, Integer installments, Integer intervalDays) {}

    private final WorkflowRequestRepository requestRepo;
    private final WorkflowProtestRepository protestRepo;
    private final PeopleService peopleService;
    private final OrgUnitService orgUnitService;
    private final OfferingService offeringService;
    private final SessionService sessionService;
    private final ProgressionRuleService progressionRuleService;
    private final CredentialService credentialService;
    private final AcademicService academicService;
    private final FeeService feeService;
    private final RoleAssignmentRepository roleAssignmentRepo;
    private final RoleRepository roleRepo;
    private final AppUserRepository appUserRepo;
    private final NotificationService notifications;
    private final AuditRecorder audit;
    private final ObjectMapper mapper;

    public WorkflowService(WorkflowRequestRepository requestRepo, WorkflowProtestRepository protestRepo,
                           PeopleService peopleService,
                           OrgUnitService orgUnitService, OfferingService offeringService,
                           SessionService sessionService, ProgressionRuleService progressionRuleService,
                           CredentialService credentialService, AcademicService academicService,
                           FeeService feeService, RoleAssignmentRepository roleAssignmentRepo, RoleRepository roleRepo,
                           AppUserRepository appUserRepo, NotificationService notifications,
                           AuditRecorder audit, ObjectMapper mapper) {
        this.requestRepo = requestRepo;
        this.protestRepo = protestRepo;
        this.peopleService = peopleService;
        this.orgUnitService = orgUnitService;
        this.offeringService = offeringService;
        this.sessionService = sessionService;
        this.progressionRuleService = progressionRuleService;
        this.credentialService = credentialService;
        this.academicService = academicService;
        this.feeService = feeService;
        this.roleAssignmentRepo = roleAssignmentRepo;
        this.roleRepo = roleRepo;
        this.appUserRepo = appUserRepo;
        this.notifications = notifications;
        this.audit = audit;
        this.mapper = mapper;
    }

    public List<WorkflowRequest> list() {
        return requestRepo.findByTenantIdOrderByCreatedAtDesc(TenantContext.getTenantId());
    }

    public WorkflowRequest get(Long id) {
        return requestRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Workflow request not found: " + id));
    }

    public List<WorkflowProtest> protestsFor(Long workflowRequestId) {
        return protestRepo.findByWorkflowRequestId(workflowRequestId);
    }

    // ---- Propose (NONE protest style) ----

    @Transactional
    public Map<String, Object> proposeOrgUnitCreate(OrgUnitReq req) {
        if (isAdmin()) {
            OrgUnit created = orgUnitService.create(req);
            save("ORG_UNIT_CREATE", req, "NONE", null, "applied");
            audit.record("ORG_UNIT_CREATED", created.getName());
            return result(true, created);
        }
        if (req.parentId() == null) {
            throw new IllegalArgumentException("Only an Institution Owner can create a top-level org unit directly");
        }
        OrgUnit parent = orgUnitService.get(req.parentId());
        WorkflowRequest wr = save("ORG_UNIT_CREATE", req, "NONE", null, "pending_confirmation");
        if (parent.getOwnerUserId() != null) {
            notifications.notify(parent.getOwnerUserId(), "WORKFLOW_PROPOSED",
                    "New sub-unit proposed under " + parent.getName(), "Awaiting your confirmation",
                    "WORKFLOW_REQUEST", wr.getId());
        }
        audit.record("ORG_UNIT_CREATE_PROPOSED", "under " + parent.getName());
        return result(false, wr);
    }

    @Transactional
    public Map<String, Object> proposeOfferingCreate(OfferingReq req) {
        if (isAdmin()) {
            var created = offeringService.create(req);
            save("OFFERING_CREATE", req, "NONE", null, "applied");
            audit.record("OFFERING_CREATED", created.getTitle());
            return result(true, created);
        }
        WorkflowRequest wr = save("OFFERING_CREATE", req, "NONE", null, "pending_confirmation");
        notifyAllAdmins("New offering proposed: " + req.title(), "Awaiting confirmation", wr.getId());
        audit.record("OFFERING_CREATE_PROPOSED", req.title());
        return result(false, wr);
    }

    @Transactional
    public Map<String, Object> proposeProgressionRuleCreate(ProgressionRuleReq req) {
        if (isAdmin()) {
            var created = progressionRuleService.create(req);
            save("PROGRESSION_RULE_CREATE", req, "NONE", null, "applied");
            audit.record("PROGRESSION_RULE_CREATED", created.getName());
            return result(true, created);
        }
        WorkflowRequest wr = save("PROGRESSION_RULE_CREATE", req, "NONE", null, "pending_confirmation");
        notifyAllAdmins("New progression rule proposed: " + req.name(), "Awaiting confirmation", wr.getId());
        audit.record("PROGRESSION_RULE_CREATE_PROPOSED", req.name());
        return result(false, wr);
    }

    /** A guardian claims their own child by the child's login handle. Never applied directly -
     *  attaching a child exposes results, attendance and fees, so an Admin always confirms. */
    @Transactional
    public Map<String, Object> initiateGuardianChildLink(GuardianClaimReq req) {
        var guardian = peopleService.guardianForUser(TenantContext.getUserId());
        var student = peopleService.studentByHandle(req.handle().trim());
        var payload = new GuardianLinkPayload(guardian.getId(), student.getId(), req.relationship());
        WorkflowRequest wr = save("GUARDIAN_CHILD_LINK", payload, "NONE", null, "pending_confirmation");
        notifyAllAdmins(guardian.getFirstName() + " " + guardian.getLastName() + " wants to link a child",
                student.getFirstName() + " " + student.getLastName() + " (" + req.handle().trim() + ")", wr.getId());
        audit.record("GUARDIAN_CHILD_LINK_PROPOSED", "student #" + student.getId() + " by guardian #" + guardian.getId());
        return result(false, wr);
    }

    /** A Credential is a bigger deal than everything else this engine handles - the requester
     *  (an Admin) can never also be the confirmer, even though the controller lets any Admin or
     *  Moderator call /confirm generically. See requireConfirmAuthority(). */
    @Transactional
    public WorkflowRequest initiateCredentialRevoke(Long credentialId, String reason) {
        credentialService.get(credentialId);
        WorkflowRequest wr = save("CREDENTIAL_REVOKE_REQUEST", new CredentialRevokePayload(credentialId, reason == null ? "" : reason),
                "NONE", null, "pending_confirmation");
        notifyAllModerators("Credential revocation requested", "Credential #" + credentialId, wr.getId());
        audit.record("CREDENTIAL_REVOKE_REQUESTED", "credential #" + credentialId);
        return wr;
    }

    @Transactional
    public Object confirm(Long id) {
        WorkflowRequest wr = get(id);
        if (!"pending_confirmation".equals(wr.getState())) {
            throw new IllegalArgumentException("That request is not awaiting confirmation");
        }
        requireConfirmAuthority(wr);
        Object applied = apply(wr);
        wr.setState("applied");
        wr.setDecidedBy(TenantContext.getUserId());
        wr.setDecidedAt(LocalDateTime.now());
        requestRepo.save(wr);
        audit.record("WORKFLOW_CONFIRMED", wr.getWorkflowType() + " #" + wr.getId());
        notifications.notify(wr.getInitiatedBy(), "WORKFLOW_CONFIRMED", "Your proposal was confirmed",
                wr.getWorkflowType(), "WORKFLOW_REQUEST", wr.getId());
        return applied;
    }

    @Transactional
    public void reject(Long id) {
        WorkflowRequest wr = get(id);
        if (!"pending_confirmation".equals(wr.getState())) {
            throw new IllegalArgumentException("That request is not awaiting confirmation");
        }
        requireConfirmAuthority(wr);
        wr.setState("rejected");
        wr.setDecidedBy(TenantContext.getUserId());
        wr.setDecidedAt(LocalDateTime.now());
        requestRepo.save(wr);
        audit.record("WORKFLOW_REJECTED", wr.getWorkflowType() + " #" + wr.getId());
        notifications.notify(wr.getInitiatedBy(), "WORKFLOW_REJECTED", "Your proposal was rejected",
                wr.getWorkflowType(), "WORKFLOW_REQUEST", wr.getId());
    }

    /** Most workflow_types are fine with the controller's blanket ADMIN gate. Credential revocation
     *  specifically needs Moderator, and an Admin (who can always confirm everything else) must
     *  NOT be able to also confirm their own revocation request - that would defeat the point of
     *  requiring a higher tier of review. */
    private void requireConfirmAuthority(WorkflowRequest wr) {
        if (!"CREDENTIAL_REVOKE_REQUEST".equals(wr.getWorkflowType())) return;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isModerator = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MODERATOR"));
        if (!isModerator) {
            throw new AccessDeniedException("Credential revocation needs a Moderator's review");
        }
    }

    /** A switch, not a Map<String,Applier> class hierarchy - proportionate for three create-types;
     *  grows into a real strategy map if more workflow_types need an applier later. */
    private Object apply(WorkflowRequest wr) {
        try {
            return switch (wr.getWorkflowType()) {
                case "ORG_UNIT_CREATE" -> orgUnitService.create(mapper.readValue(wr.getPayload(), OrgUnitReq.class));
                case "OFFERING_CREATE" -> offeringService.create(mapper.readValue(wr.getPayload(), OfferingReq.class));
                case "PROGRESSION_RULE_CREATE" -> progressionRuleService.create(mapper.readValue(wr.getPayload(), ProgressionRuleReq.class));
                case "CREDENTIAL_REVOKE_REQUEST" -> {
                    var payload = mapper.readValue(wr.getPayload(), CredentialRevokePayload.class);
                    credentialService.revoke(payload.credentialId());
                    yield payload;
                }
                case "GUARDIAN_CHILD_LINK" -> {
                    var payload = mapper.readValue(wr.getPayload(), GuardianLinkPayload.class);
                    yield peopleService.linkGuardianChild(payload.guardianId(), payload.studentId(), payload.relationship());
                }
                case "INSTALLMENT_REQUEST" -> {
                    var payload = mapper.readValue(wr.getPayload(), InstallmentPayload.class);
                    yield feeService.splitIntoInstallments(payload.invoiceId(), payload.installments(), payload.intervalDays());
                }
                default -> throw new IllegalStateException("No applier for workflow type " + wr.getWorkflowType());
            };
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt workflow payload for request " + wr.getId(), e);
        }
    }

    // ---- Org Unit deletion (FORMAL protest style, 7-day window) ----

    @Transactional
    public WorkflowRequest initiateOrgUnitDelete(Long orgUnitId) {
        OrgUnit unit = orgUnitService.get(orgUnitId);
        orgUnitService.setStatus(orgUnitId, "pending_deletion");
        WorkflowRequest wr = save("ORG_UNIT_DELETE", new DeletePayload(orgUnitId), "FORMAL",
                LocalDateTime.now().plusDays(7), "pending_confirmation");
        for (Long recipientId : branchRecipients(orgUnitId)) {
            notifications.notify(recipientId, "ORG_UNIT_DELETE_PROPOSED",
                    "\"" + unit.getName() + "\" is up for deletion", "You have 7 days to protest",
                    "WORKFLOW_REQUEST", wr.getId());
        }
        audit.record("ORG_UNIT_DELETE_INITIATED", unit.getName());
        return wr;
    }

    // ---- Session cancellation (COMMENT protest style: already applied, thread stays open) ----

    /** The cancellation itself is immediate (a status flag); the workflow_request row exists to
     *  host the comment thread via the same raiseProtest()/protestsFor() used elsewhere. */
    @Transactional
    public WorkflowRequest initiateSessionCancel(Long sessionId, String reason) {
        sessionService.setStatus(sessionId, "cancelled");
        WorkflowRequest wr = save("SESSION_CANCEL", Map.of("sessionId", sessionId, "reason", reason == null ? "" : reason),
                "COMMENT", null, "applied");
        audit.record("SESSION_CANCEL_INITIATED", "session #" + sessionId);
        return wr;
    }

    // ---- Result dispute (COMMENT protest style, no entity mutation - routed to the teacher) ----

    @Transactional
    public WorkflowRequest initiateResultDispute(Long resultId, String reason) {
        WorkflowRequest wr = save("RESULT_DISPUTE", new ResultDisputePayload(resultId, reason == null ? "" : reason),
                "COMMENT", null, "applied");
        Long teacherUserId = academicService.resolveTeacherUserIdForResult(resultId);
        if (teacherUserId != null) {
            notifications.notify(teacherUserId, "RESULT_DISPUTED", "A result was disputed",
                    reason, "WORKFLOW_REQUEST", wr.getId());
        }
        audit.record("RESULT_DISPUTE_RAISED", "result #" + resultId);
        return wr;
    }

    // ---- Installment request (NONE protest style: ADMIN/BURSAR have standing permission,
    // a student/guardian proposing on their own invoice needs Admin/Moderator confirmation) ----

    @Transactional
    public Object initiateInstallmentRequest(Long invoiceId, Integer installments, Integer intervalDays, String reason) {
        if (installments == null || installments < 2) throw new IllegalArgumentException("Need at least 2 installments");
        int interval = intervalDays == null ? 30 : intervalDays;
        var payload = new InstallmentPayload(invoiceId, installments, interval);

        if (isAdmin() || isBursar()) {
            var applied = feeService.splitIntoInstallments(invoiceId, installments, interval);
            save("INSTALLMENT_REQUEST", payload, "NONE", null, "applied");
            audit.record("INSTALLMENT_REQUEST_APPLIED", "invoice #" + invoiceId + " into " + installments + " parts");
            return result(true, applied);
        }
        feeService.assertCallerOwnsInvoice(invoiceId);
        WorkflowRequest wr = save("INSTALLMENT_REQUEST", payload, "NONE", null, "pending_confirmation");
        notifyAllAdmins("Installment plan requested", "Invoice #" + invoiceId + " into " + installments + " parts", wr.getId());
        audit.record("INSTALLMENT_REQUEST_PROPOSED", "invoice #" + invoiceId);
        return result(false, wr);
    }

    @Transactional
    public WorkflowProtest raiseProtest(Long workflowRequestId, String comment) {
        WorkflowRequest wr = get(workflowRequestId);
        boolean open = "pending_confirmation".equals(wr.getState()) || "COMMENT".equals(wr.getProtestStyle());
        if (!open) {
            throw new IllegalArgumentException("That request is no longer open to protest");
        }
        WorkflowProtest p = new WorkflowProtest();
        p.setWorkflowRequestId(workflowRequestId);
        p.setRaisedByUserId(TenantContext.getUserId());
        p.setComment(comment);
        WorkflowProtest saved = protestRepo.save(p);
        audit.record("WORKFLOW_PROTEST_RAISED", wr.getWorkflowType() + " #" + wr.getId());
        notifications.notify(wr.getInitiatedBy(), "WORKFLOW_PROTESTED",
                "A protest was raised against your request", wr.getWorkflowType(), "WORKFLOW_REQUEST", wr.getId());
        return saved;
    }

    /**
     * Seconding stops the underlying action - inverted polarity from confirm/reject, where
     * confirming is what MAKES something happen. But a single objector's second does not veto
     * outright: it ESCALATES (tier 1->2, protest window extended 3 days, all Moderators
     * notified) and the protest stays open. Only a Moderator's decision at tier>=2 is final -
     * nothing sensitive is cancelled by one person's say-so alone.
     */
    @Transactional
    public void secondProtest(Long protestId) {
        WorkflowProtest p = getProtest(protestId);
        WorkflowRequest wr = get(p.getWorkflowRequestId());

        if (wr.getApproverTier() < 2 && !isModerator()) {
            wr.setApproverTier(2);
            if (wr.getProtestDeadline() != null) wr.setProtestDeadline(LocalDateTime.now().plusDays(3));
            requestRepo.save(wr);
            notifyAllModerators("Protest escalated - needs your review", wr.getWorkflowType() + " #" + wr.getId(), wr.getId());
            audit.record("WORKFLOW_PROTEST_ESCALATED", wr.getWorkflowType() + " #" + wr.getId() + " -> tier 2");
            return;
        }

        p.setStatus("seconded");
        p.setDecidedBy(TenantContext.getUserId());
        p.setDecidedAt(LocalDateTime.now());
        protestRepo.save(p);

        wr.setState("cancelled");
        wr.setDecidedBy(TenantContext.getUserId());
        wr.setDecidedAt(LocalDateTime.now());
        requestRepo.save(wr);
        revertIfOrgUnitDelete(wr);

        audit.record("WORKFLOW_PROTEST_SECONDED", wr.getWorkflowType() + " #" + wr.getId());
        notifications.notify(wr.getInitiatedBy(), "WORKFLOW_CANCELLED",
                "Your request was cancelled after a protest was seconded", wr.getWorkflowType(),
                "WORKFLOW_REQUEST", wr.getId());
    }

    /**
     * Dismissing clears just this protest. If it was the last open one AND the window has
     * already closed, the request applies immediately; otherwise it's picked up by the next
     * sweep once the window closes (assuming no new protest arrives before then) - a simpler
     * version of "goes through the same process again" than a full fresh-draft restart.
     */
    @Transactional
    public void dismissProtest(Long protestId) {
        WorkflowProtest p = getProtest(protestId);
        p.setStatus("dismissed");
        p.setDecidedBy(TenantContext.getUserId());
        p.setDecidedAt(LocalDateTime.now());
        protestRepo.save(p);
        audit.record("WORKFLOW_PROTEST_DISMISSED", "protest #" + protestId);

        WorkflowRequest wr = get(p.getWorkflowRequestId());
        boolean anyStillOpen = !protestRepo.findByWorkflowRequestIdAndStatus(wr.getId(), "open").isEmpty();
        if (!anyStillOpen && wr.getProtestDeadline() != null && !wr.getProtestDeadline().isAfter(LocalDateTime.now())) {
            applyFormal(wr);
        }
    }

    /** Sweeps for FORMAL requests whose window has closed with no open protest left. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void sweepExpiredWindows() {
        for (WorkflowRequest wr : requestRepo.findByStateAndProtestDeadlineLessThanEqual("pending_confirmation", LocalDateTime.now())) {
            if (protestRepo.findByWorkflowRequestIdAndStatus(wr.getId(), "open").isEmpty()) {
                applyFormal(wr);
            }
        }
    }

    private void applyFormal(WorkflowRequest wr) {
        if ("ORG_UNIT_DELETE".equals(wr.getWorkflowType())) {
            orgUnitService.hardDelete(readDeletePayload(wr).orgUnitId());
        }
        wr.setState("applied");
        wr.setDecidedAt(LocalDateTime.now());
        requestRepo.save(wr);
        audit.record("WORKFLOW_APPLIED", wr.getWorkflowType() + " #" + wr.getId());
    }

    private void revertIfOrgUnitDelete(WorkflowRequest wr) {
        if (!"ORG_UNIT_DELETE".equals(wr.getWorkflowType())) return;
        orgUnitService.setStatus(readDeletePayload(wr).orgUnitId(), "active");
    }

    private DeletePayload readDeletePayload(WorkflowRequest wr) {
        try {
            return mapper.readValue(wr.getPayload(), DeletePayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt workflow payload for request " + wr.getId(), e);
        }
    }

    private WorkflowProtest getProtest(Long id) {
        return protestRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Protest not found: " + id));
    }

    // ---- helpers ----

    private List<Long> branchRecipients(Long orgUnitId) {
        List<Long> unitIds = orgUnitService.descendantIds(orgUnitId);
        Set<Long> recipients = roleAssignmentRepo.findByScopeTypeAndScopeRefIdInAndStatus("ORG_UNIT", unitIds, "active")
                .stream().map(RoleAssignment::getAppUserId).collect(Collectors.toSet());
        OrgUnit unit = orgUnitService.get(orgUnitId);
        if (unit.getOwnerUserId() != null) recipients.add(unit.getOwnerUserId());
        return recipients.stream().toList();
    }

    private void notifyAllAdmins(String title, String body, Long linkId) {
        Role admin = roleRepo.findByName("ADMIN").orElseThrow(() -> new IllegalStateException("ADMIN role missing"));
        for (var user : appUserRepo.findByRoleIdAndTenantId(admin.getId(), TenantContext.getTenantId())) {
            notifications.notify(user.getId(), "WORKFLOW_PROPOSED", title, body, "WORKFLOW_REQUEST", linkId);
        }
    }

    private void notifyAllModerators(String title, String body, Long linkId) {
        Role moderator = roleRepo.findByName("MODERATOR").orElseThrow(() -> new IllegalStateException("MODERATOR role missing"));
        for (var user : appUserRepo.findByRoleIdAndTenantId(moderator.getId(), TenantContext.getTenantId())) {
            notifications.notify(user.getId(), "CREDENTIAL_REVOKE_REQUESTED", title, body, "WORKFLOW_REQUEST", linkId);
        }
    }

    private boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean isBursar() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_BURSAR"));
    }

    private boolean isModerator() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MODERATOR"));
    }

    private WorkflowRequest save(String type, Object payload, String protestStyle, LocalDateTime deadline, String state) {
        WorkflowRequest wr = new WorkflowRequest();
        wr.setTenantId(TenantContext.getTenantId());
        wr.setWorkflowType(type);
        try {
            wr.setPayload(mapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize workflow payload", e);
        }
        wr.setProtestStyle(protestStyle);
        wr.setState(state);
        wr.setProtestDeadline(deadline);
        wr.setInitiatedBy(TenantContext.getUserId());
        if ("applied".equals(state)) {
            wr.setDecidedBy(TenantContext.getUserId());
            wr.setDecidedAt(LocalDateTime.now());
        }
        return requestRepo.save(wr);
    }

    private Map<String, Object> result(boolean applied, Object payload) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applied", applied);
        m.put(applied ? "result" : "workflowRequest", payload);
        return m;
    }
}
