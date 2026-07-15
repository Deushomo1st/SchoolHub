package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.*;
import com.schoolhub.schoolservice.service.AcademicService;
import com.schoolhub.schoolservice.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AcademicController {

    private final AcademicService academic;
    private final WorkflowService workflow;

    public AcademicController(AcademicService academic, WorkflowService workflow) {
        this.academic = academic;
        this.workflow = workflow;
    }

    // ---- Subjects ----
    @GetMapping("/subjects")
    public ResponseEntity<?> subjects() { return ResponseEntity.ok(academic.listSubjects()); }

    @PostMapping("/subjects")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createSubject(@Valid @RequestBody SubjectReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academic.createSubject(req));
    }

    @DeleteMapping("/subjects/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteSubject(@PathVariable Long id) {
        academic.deleteSubject(id);
        return ResponseEntity.ok().build();
    }

    // ---- Classes ----
    @GetMapping("/classes")
    public ResponseEntity<?> classes() { return ResponseEntity.ok(academic.listClasses()); }

    @GetMapping("/classes/{id}")
    public ResponseEntity<?> getClass(@PathVariable Long id) { return ResponseEntity.ok(academic.getClass(id)); }

    @PostMapping("/classes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createClass(@Valid @RequestBody ClassReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academic.createClass(req));
    }

    @PutMapping("/classes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateClass(@PathVariable Long id, @Valid @RequestBody ClassReq req) {
        return ResponseEntity.ok(academic.updateClass(id, req));
    }

    @DeleteMapping("/classes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteClass(@PathVariable Long id) {
        academic.deleteClass(id);
        return ResponseEntity.ok().build();
    }

    // ---- Class-subject assignments ----
    @GetMapping("/class-subjects")
    public ResponseEntity<?> classSubjects(@RequestParam(required = false) Long classId) {
        return ResponseEntity.ok(academic.listClassSubjects(classId));
    }

    @PostMapping("/class-subjects")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> assign(@Valid @RequestBody ClassSubjectReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academic.assignSubject(req));
    }

    @DeleteMapping("/class-subjects/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> unassign(@PathVariable Long id) {
        academic.removeClassSubject(id);
        return ResponseEntity.ok().build();
    }

    // ---- Assessments + results ----
    // classSubjectId (old model) or cohortOfferingId (new model) - either narrows the list.
    @GetMapping("/assessments")
    public ResponseEntity<?> assessments(@RequestParam(required = false) Long classSubjectId,
                                         @RequestParam(required = false) Long cohortOfferingId) {
        if (cohortOfferingId != null) return ResponseEntity.ok(academic.listAssessmentsForCohortOffering(cohortOfferingId));
        return ResponseEntity.ok(academic.listAssessments(classSubjectId));
    }

    @PostMapping("/assessments")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> createAssessment(@Valid @RequestBody AssessmentReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academic.createAssessment(req));
    }

    // The teacher's release gate - unpublished stays invisible to students until flipped.
    @PostMapping("/assessments/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> publish(@PathVariable Long id) {
        return ResponseEntity.ok(academic.setPublished(id, true));
    }

    @PostMapping("/assessments/{id}/unpublish")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> unpublish(@PathVariable Long id) {
        return ResponseEntity.ok(academic.setPublished(id, false));
    }

    // On-demand, never stored/frozen - regenerated from live Result rows every call.
    @GetMapping("/transcript")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','BURSAR')")
    public ResponseEntity<?> transcript(@RequestParam Long studentId, @RequestParam Long cohortOfferingId) {
        return ResponseEntity.ok(academic.transcript(studentId, cohortOfferingId));
    }

    // Other students' scores are private -> staff only. A student sees own results via /me/student.
    @GetMapping("/results")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','BURSAR')")
    public ResponseEntity<?> results(@RequestParam Long assessmentId) {
        return ResponseEntity.ok(academic.listResults(assessmentId));
    }

    @PostMapping("/results")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> recordResult(@Valid @RequestBody ResultReq req) {
        return ResponseEntity.ok(academic.recordResult(req));
    }

    // Lightweight - a comment thread via the same generic workflow-requests/{id}/protests
    // endpoints used everywhere else, not a bespoke dispute mechanism.
    @PostMapping("/results/{id}/dispute")
    public ResponseEntity<?> disputeResult(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(workflow.initiateResultDispute(id, reason));
    }

    // ---- Attendance ----
    // Another student's attendance is private -> staff only.
    @GetMapping("/attendance")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','BURSAR')")
    public ResponseEntity<?> attendance(@RequestParam Long studentId) {
        return ResponseEntity.ok(academic.listAttendance(studentId));
    }

    @PostMapping("/attendance")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> markAttendance(@Valid @RequestBody AttendanceReq req) {
        return ResponseEntity.ok(academic.markAttendance(req));
    }
}
