package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.EnrollmentCloseReq;
import com.schoolhub.schoolservice.dto.Requests.EnrollmentReq;
import com.schoolhub.schoolservice.model.Enrollment;
import com.schoolhub.schoolservice.repository.EnrollmentRepository;
import com.schoolhub.schoolservice.repository.StudentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Enrollment is the hub: it binds a Student to a Cohort and/or an Offering/Group for one
 * schedule_period, replacing the old build's raw Student.classId column as the source of
 * truth. Student.classId is left untouched here (existing dashboards keep reading it as-is,
 * zero regression risk) - PerspectiveService instead prefers Enrollment data when it exists,
 * falling back to the legacy column when it doesn't (see currentHomeCohort()).
 */
@Service
public class EnrollmentService {

    private static final Set<String> CLOSED_STATUSES = Set.of("transferred_out", "withdrawn", "graduated", "repeating");

    private final EnrollmentRepository repo;
    private final StudentRepository studentRepo;

    public EnrollmentService(EnrollmentRepository repo, StudentRepository studentRepo) {
        this.repo = repo;
        this.studentRepo = studentRepo;
    }

    public List<Enrollment> listForStudent(Long studentId) {
        return repo.findByStudentIdOrderByCreatedAtDesc(studentId);
    }

    public List<Enrollment> listForCohort(Long cohortId) {
        return repo.findByCohortId(cohortId);
    }

    /** The student's current home-cohort Enrollment, if any - the Enrollment-aware dashboard read path. */
    public Enrollment currentHomeCohort(Long studentId) {
        return repo.findFirstByStudentIdAndCohortIdIsNotNullAndStatusOrderByCreatedAtDesc(studentId, "active")
                .orElse(null);
    }

    @Transactional
    public Enrollment create(EnrollmentReq req) {
        if (!studentRepo.existsById(req.studentId())) {
            throw new EntityNotFoundException("Student not found: " + req.studentId());
        }
        if (req.cohortId() == null && req.offeringId() == null && req.groupId() == null) {
            throw new IllegalArgumentException("An enrollment must target a cohort, an offering, or a group");
        }
        Enrollment e = new Enrollment();
        e.setStudentId(req.studentId());
        e.setCohortId(req.cohortId());
        e.setOfferingId(req.offeringId());
        e.setGroupId(req.groupId());
        e.setSchedulePeriodId(req.schedulePeriodId());
        return repo.save(e);
    }

    /**
     * Snapshot-on-close: freeze the enrollment's status/end date rather than deleting it, so its
     * Attendance/Results stay historically attached to this exact row.
     */
    @Transactional
    public Enrollment close(Long id, EnrollmentCloseReq req) {
        Enrollment e = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Enrollment not found: " + id));
        String status = req.status() == null ? "" : req.status().toLowerCase();
        if (!CLOSED_STATUSES.contains(status)) {
            throw new IllegalArgumentException("status must be one of " + CLOSED_STATUSES);
        }
        e.setStatus(status);
        e.setClosedReason(req.reason());
        e.setEndedOn(LocalDate.now());
        return repo.save(e);
    }
}
