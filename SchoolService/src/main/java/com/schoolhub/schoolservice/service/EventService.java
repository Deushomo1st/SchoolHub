package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.EventReq;
import com.schoolhub.schoolservice.model.*;
import com.schoolhub.schoolservice.repository.*;
import com.schoolhub.schoolservice.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** School calendar: events, announcements, holidays, exams. Audience may be several
 *  groups at once (comma list); any recipient can forward an event to their reachables. */
@Service
public class EventService {

    private static final Set<String> AUDIENCES = Set.of("all", "staff", "students", "guardians");

    private final CalendarEventRepository repo;
    private final StudentRepository studentRepo;
    private final GuardianRepository guardianRepo;
    private final TeacherRepository teacherRepo;
    private final StudentGuardianRepository linkRepo;
    private final NotificationService notifications;

    public EventService(CalendarEventRepository repo, StudentRepository studentRepo,
                        GuardianRepository guardianRepo, TeacherRepository teacherRepo,
                        StudentGuardianRepository linkRepo, NotificationService notifications) {
        this.repo = repo;
        this.studentRepo = studentRepo;
        this.guardianRepo = guardianRepo;
        this.teacherRepo = teacherRepo;
        this.linkRepo = linkRepo;
        this.notifications = notifications;
    }

    public List<CalendarEvent> list() { return repo.findAllByOrderByStartDateDesc(); }

    @Transactional
    public CalendarEvent create(EventReq req) {
        CalendarEvent e = new CalendarEvent();
        e.setTitle(req.title());
        e.setDescription(req.description());
        if (req.eventType() != null) e.setEventType(req.eventType());
        if (req.audience() != null) e.setAudience(normalizeAudience(req.audience()));
        e.setStartDate(req.startDate());
        e.setEndDate(req.endDate());
        e.setCreatedBy(TenantContext.getUserId());
        return repo.save(e);
    }

    /** "staff,students" style multi-select; 'all' (or everything ticked) collapses to 'all'. */
    private String normalizeAudience(String raw) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        for (String p : raw.toLowerCase().split(",")) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            if (!AUDIENCES.contains(t)) throw new IllegalArgumentException("Unknown audience: " + t);
            parts.add(t);
        }
        if (parts.isEmpty() || parts.contains("all") || parts.size() >= 3) return "all";
        return String.join(",", parts);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new EntityNotFoundException("Event not found: " + id);
        repo.deleteById(id);
    }

    // ---- Forwarding: a recipient pushes an event to their reachables as notifications ----

    /** Who each role may forward to. */
    public List<String> allowedTargets() {
        return switch (callerRole()) {
            case "STUDENT" -> List.of("guardians");
            case "PARENT" -> List.of("children");
            case "TEACHER", "BURSAR", "LIBRARIAN" -> List.of("students", "guardians");
            case "ADMIN" -> List.of("students", "guardians", "teachers");
            default -> List.of();
        };
    }

    @Transactional
    public Map<String, Object> forward(Long eventId, String target) {
        CalendarEvent e = repo.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));
        String t = target == null ? "" : target.trim().toLowerCase();
        if (!allowedTargets().contains(t)) {
            throw new AccessDeniedException("Your role can't forward to '" + t + "'");
        }
        Set<Long> userIds = recipientsFor(t);
        userIds.remove(TenantContext.getUserId());       // never forward to yourself
        String body = e.getTitle() + " · " + e.getStartDate() + " (forwarded to you)";
        for (Long uid : userIds) {
            notifications.notify(uid, "event_forwarded", "Event: " + e.getTitle(), body, "event", e.getId());
        }
        return Map.of("sent", userIds.size(), "target", t);
    }

    private Set<Long> recipientsFor(String target) {
        Long callerUid = TenantContext.getUserId();
        Set<Long> out = new HashSet<>();
        switch (target) {
            case "guardians" -> {
                Student self = studentRepo.findByUserId(callerUid).orElse(null);
                if (self != null) {
                    // A student reaches only their own guardians.
                    for (StudentGuardian l : linkRepo.findByStudentId(self.getId())) {
                        guardianRepo.findById(l.getGuardianId())
                                .map(Guardian::getUserId).filter(Objects::nonNull).ifPresent(out::add);
                    }
                } else {
                    guardianRepo.findAll().forEach(g -> { if (g.getUserId() != null) out.add(g.getUserId()); });
                }
            }
            case "children" -> {
                Guardian g = guardianRepo.findByUserId(callerUid)
                        .orElseThrow(() -> new AccessDeniedException("No guardian profile"));
                for (StudentGuardian l : linkRepo.findByGuardianId(g.getId())) {
                    studentRepo.findById(l.getStudentId())
                            .map(Student::getUserId).filter(Objects::nonNull).ifPresent(out::add);
                }
            }
            case "students" -> studentRepo.findAll().forEach(s -> { if (s.getUserId() != null) out.add(s.getUserId()); });
            case "teachers" -> teacherRepo.findAll().forEach(tc -> { if (tc.getUserId() != null) out.add(tc.getUserId()); });
            default -> throw new IllegalArgumentException("Unknown target: " + target);
        }
        return out;
    }

    private String callerRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "";
        // Highest-privilege first: PRINCIPAL also carries ROLE_ADMIN.
        for (String r : List.of("ADMIN", "TEACHER", "BURSAR", "LIBRARIAN", "PARENT", "STUDENT")) {
            if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + r))) return r;
        }
        return "";
    }
}
