package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.SessionReq;
import com.schoolhub.schoolservice.model.CohortOffering;
import com.schoolhub.schoolservice.model.Resource;
import com.schoolhub.schoolservice.model.Session;
import com.schoolhub.schoolservice.model.Teacher;
import com.schoolhub.schoolservice.repository.CohortOfferingRepository;
import com.schoolhub.schoolservice.repository.EnrollmentRepository;
import com.schoolhub.schoolservice.repository.ResourceRepository;
import com.schoolhub.schoolservice.repository.SessionRepository;
import com.schoolhub.schoolservice.repository.TeacherRepository;
import com.schoolhub.schoolservice.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A single time-bound teaching act. Booking a Resource is soft/overridable, not a hard block -
 * see checkConflict(). Cancellation is a lightweight, comment-thread-style workflow, handled by
 * WorkflowService (initiateSessionCancel) rather than the heavy Org-Unit-style protest window;
 * this class only exposes the plain status/time mutators that workflow needs.
 */
@Service
public class SessionService {

    public record ConflictInfo(boolean conflict, int projectedAudience, Integer capacity, List<Long> overlappingSessionIds) {}

    private final SessionRepository repo;
    private final ResourceRepository resourceRepo;
    private final CohortOfferingRepository cohortOfferingRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final TeacherRepository teacherRepo;
    private final NotificationService notifications;
    private final AuditRecorder audit;

    public SessionService(SessionRepository repo, ResourceRepository resourceRepo,
                          CohortOfferingRepository cohortOfferingRepo, EnrollmentRepository enrollmentRepo,
                          TeacherRepository teacherRepo, NotificationService notifications, AuditRecorder audit) {
        this.repo = repo;
        this.resourceRepo = resourceRepo;
        this.cohortOfferingRepo = cohortOfferingRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.teacherRepo = teacherRepo;
        this.notifications = notifications;
        this.audit = audit;
    }

    public List<Session> listForCohortOffering(Long cohortOfferingId) {
        return repo.findByCohortOfferingIdOrderByStartAtDesc(cohortOfferingId);
    }

    public Session get(Long id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Session not found: " + id));
    }

    @Transactional
    public Session create(SessionReq req) {
        CohortOffering co = cohortOfferingRepo.findById(req.cohortOfferingId())
                .orElseThrow(() -> new EntityNotFoundException("Cohort-offering not found: " + req.cohortOfferingId()));
        if (req.resourceId() != null) {
            ConflictInfo conflict = checkConflict(req.resourceId(), req.startAt(), req.endAt(), null, co.getCohortId());
            if (conflict.conflict()) {
                if (!Boolean.TRUE.equals(req.override())) {
                    throw new IllegalArgumentException("Resource capacity conflict: " + conflict.projectedAudience()
                            + " expected attendees exceeds capacity " + conflict.capacity()
                            + " for overlapping bookings. Set override=true to book anyway.");
                }
                notifyOverlappingTeachers(conflict.overlappingSessionIds());
            }
        }
        Session s = new Session();
        s.setCohortOfferingId(req.cohortOfferingId());
        s.setGroupId(req.groupId());
        s.setResourceId(req.resourceId());
        if (req.sessionType() != null && !req.sessionType().isBlank()) s.setSessionType(req.sessionType());
        s.setTitle(req.title());
        s.setStartAt(req.startAt());
        s.setEndAt(req.endAt());
        s.setRecurrenceRule(req.recurrenceRule());
        s.setCreatedBy(TenantContext.getUserId());
        Session saved = repo.save(s);
        audit.record("SESSION_CREATED", "cohort-offering #" + req.cohortOfferingId());
        return saved;
    }

    /**
     * Soft, capacity-aware: sums the audience of every overlapping, non-cancelled session on the
     * same Resource plus this new one's, using each Cohort's active-enrollment count as a safe
     * conservative audience-size estimate (a Group is always a subset, so this never undercounts
     * the risk). Returns the conflict info rather than throwing - the caller decides to override.
     */
    public ConflictInfo checkConflict(Long resourceId, LocalDateTime start, LocalDateTime end, Long excludeSessionId, Long cohortId) {
        Resource r = resourceRepo.findById(resourceId)
                .orElseThrow(() -> new EntityNotFoundException("Resource not found: " + resourceId));
        if (r.getCapacity() == null) return new ConflictInfo(false, 0, null, List.of());
        List<Session> overlapping = repo.findByResourceIdAndStatusNot(resourceId, "cancelled").stream()
                .filter(s -> excludeSessionId == null || !s.getId().equals(excludeSessionId))
                .filter(s -> s.getStartAt().isBefore(end) && s.getEndAt().isAfter(start))
                .toList();
        int projected = audienceSize(cohortId);
        for (Session s : overlapping) {
            var co = cohortOfferingRepo.findById(s.getCohortOfferingId()).orElse(null);
            if (co != null) projected += audienceSize(co.getCohortId());
        }
        boolean conflict = projected > r.getCapacity();
        return new ConflictInfo(conflict, projected, r.getCapacity(), overlapping.stream().map(Session::getId).toList());
    }

    @Transactional
    public Session reschedule(Long id, LocalDateTime newStart, LocalDateTime newEnd) {
        Session s = get(id);
        s.setStartAt(newStart);
        s.setEndAt(newEnd);
        s.setStatus("rescheduled");
        Session saved = repo.save(s);
        audit.record("SESSION_RESCHEDULED", "session #" + id);
        return saved;
    }

    @Transactional
    public void setStatus(Long id, String status) {
        Session s = get(id);
        s.setStatus(status);
        repo.save(s);
    }

    private int audienceSize(Long cohortId) {
        return (int) enrollmentRepo.findByCohortId(cohortId).stream()
                .filter(e -> "active".equals(e.getStatus())).count();
    }

    private void notifyOverlappingTeachers(List<Long> sessionIds) {
        for (Long sid : sessionIds) {
            Session s = get(sid);
            CohortOffering co = cohortOfferingRepo.findById(s.getCohortOfferingId()).orElse(null);
            if (co == null || co.getTeacherId() == null) continue;
            Teacher t = teacherRepo.findById(co.getTeacherId()).orElse(null);
            if (t != null && t.getUserId() != null) {
                notifications.notify(t.getUserId(), "SESSION_RESOURCE_OVERRIDE",
                        "A session was booked into your room despite a capacity conflict",
                        "Session #" + sid, "SESSION", sid);
            }
        }
    }
}
