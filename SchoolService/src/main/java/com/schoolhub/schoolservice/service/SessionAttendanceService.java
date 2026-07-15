package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.SessionAttendanceReq;
import com.schoolhub.schoolservice.model.SessionAttendance;
import com.schoolhub.schoolservice.repository.SessionAttendanceRepository;
import com.schoolhub.schoolservice.repository.SessionRepository;
import com.schoolhub.schoolservice.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** Per-session attendance, marked by the handling teacher or an appointed moderator. */
@Service
public class SessionAttendanceService {

    private static final Set<String> STATUSES = Set.of("present", "absent", "late", "excused");

    private final SessionAttendanceRepository repo;
    private final SessionRepository sessionRepo;

    public SessionAttendanceService(SessionAttendanceRepository repo, SessionRepository sessionRepo) {
        this.repo = repo;
        this.sessionRepo = sessionRepo;
    }

    public List<SessionAttendance> listForSession(Long sessionId) { return repo.findBySessionId(sessionId); }

    /** Mark (or correct) a student's attendance for a session - upsert by (session, student). */
    @Transactional
    public SessionAttendance mark(Long sessionId, SessionAttendanceReq req) {
        if (!sessionRepo.existsById(sessionId)) throw new EntityNotFoundException("Session not found: " + sessionId);
        String status = req.status().toLowerCase();
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("status must be one of " + STATUSES);
        SessionAttendance a = repo.findBySessionIdAndStudentId(sessionId, req.studentId()).orElseGet(SessionAttendance::new);
        a.setSessionId(sessionId);
        a.setStudentId(req.studentId());
        a.setStatus(status);
        a.setMarkedBy(TenantContext.getUserId());
        return repo.save(a);
    }
}
