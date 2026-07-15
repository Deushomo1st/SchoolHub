package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.model.*;
import com.schoolhub.schoolservice.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Assembles the per-role dashboards. Each method returns a ready-to-render bundle
 * for one persona, joining the linked tables so the UI stays thin.
 */
@Service
public class PerspectiveService {

    private final TeacherRepository teacherRepo;
    private final StudentRepository studentRepo;
    private final GuardianRepository guardianRepo;
    private final SchoolClassRepository classRepo;
    private final SubjectRepository subjectRepo;
    private final ClassSubjectRepository classSubjectRepo;
    private final AssessmentRepository assessmentRepo;
    private final ResultRepository resultRepo;
    private final AttendanceRepository attendanceRepo;
    private final StudentGuardianRepository linkRepo;
    private final CalendarEventRepository eventRepo;
    private final FeeService feeService;
    private final EnrollmentService enrollmentService;
    private final CohortRepository cohortRepo;

    public PerspectiveService(TeacherRepository teacherRepo, StudentRepository studentRepo,
                              GuardianRepository guardianRepo, SchoolClassRepository classRepo,
                              SubjectRepository subjectRepo, ClassSubjectRepository classSubjectRepo,
                              AssessmentRepository assessmentRepo, ResultRepository resultRepo,
                              AttendanceRepository attendanceRepo, StudentGuardianRepository linkRepo,
                              CalendarEventRepository eventRepo, FeeService feeService,
                              EnrollmentService enrollmentService, CohortRepository cohortRepo) {
        this.feeService = feeService;
        this.teacherRepo = teacherRepo;
        this.studentRepo = studentRepo;
        this.guardianRepo = guardianRepo;
        this.classRepo = classRepo;
        this.subjectRepo = subjectRepo;
        this.classSubjectRepo = classSubjectRepo;
        this.assessmentRepo = assessmentRepo;
        this.resultRepo = resultRepo;
        this.attendanceRepo = attendanceRepo;
        this.linkRepo = linkRepo;
        this.eventRepo = eventRepo;
        this.enrollmentService = enrollmentService;
        this.cohortRepo = cohortRepo;
    }

    // ---- School owner (ADMIN) summary ----
    public Map<String, Object> schoolSummary() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("teachers", teacherRepo.count());
        counts.put("students", studentRepo.count());
        counts.put("guardians", guardianRepo.count());
        counts.put("classes", classRepo.count());
        counts.put("subjects", subjectRepo.count());
        m.put("counts", counts);
        m.put("events", eventRepo.findAllByOrderByStartDateDesc());
        return m;
    }

    // ---- Teacher ----
    public Map<String, Object> teacherDashboard(Long userId) {
        Teacher t = teacherRepo.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("No teacher profile is linked to your account"));
        Map<Long, String> classNames = nameMap(classRepo.findAll(), SchoolClass::getId, SchoolClass::getName);
        Map<Long, String> subjectNames = nameMap(subjectRepo.findAll(), Subject::getId, Subject::getName);

        List<Map<String, Object>> assignments = classSubjectRepo.findByTeacherId(t.getId()).stream().map(cs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("classSubjectId", cs.getId());
            row.put("classId", cs.getClassId());
            row.put("className", classNames.get(cs.getClassId()));
            row.put("subjectName", subjectNames.get(cs.getSubjectId()));
            return row;
        }).collect(Collectors.toList());

        List<SchoolClass> myClasses = classRepo.findAll().stream()
                .filter(c -> t.getId().equals(c.getClassTeacherId())).collect(Collectors.toList());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profile", t);
        m.put("assignments", assignments);
        m.put("myClasses", myClasses);
        return m;
    }

    // ---- Student ----
    public Map<String, Object> studentDashboard(Long userId) {
        Student s = studentRepo.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("No student profile is linked to your account"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profile", s);
        m.put("className", resolveClassName(s));
        m.put("subjects", subjectsForClass(s.getClassId()));
        m.put("results", resultRows(s.getId()));
        m.put("attendance", attendanceSummary(s.getId()));
        m.put("fees", feeService.invoicesForStudent(s.getId()));
        return m;
    }

    // ---- Guardian (parent) - track each child's performance ----
    public Map<String, Object> guardianDashboard(Long userId) {
        Guardian g = guardianRepo.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("No guardian profile is linked to your account"));

        List<Map<String, Object>> children = new ArrayList<>();
        for (StudentGuardian link : linkRepo.findByGuardianId(g.getId())) {
            Optional<Student> child = studentRepo.findById(link.getStudentId());
            if (child.isEmpty()) continue;
            Student s = child.get();
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("student", s);
            c.put("relationship", link.getRelationship());
            c.put("className", resolveClassName(s));
            c.put("results", resultRows(s.getId()));
            c.put("attendance", attendanceSummary(s.getId()));
            c.put("fees", feeService.invoicesForStudent(s.getId()));
            children.add(c);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profile", g);
        m.put("children", children);
        return m;
    }

    // ---- shared assembly ----

    /**
     * Enrollment-aware: prefers the student's current home-Cohort Enrollment if one exists,
     * falling back to the legacy Student.classId -> SchoolClass lookup otherwise. Lets Cohort
     * (the new universal-model grouping) and SchoolClass (the old one) coexist during the
     * transition without either dashboard regressing.
     */
    private String resolveClassName(Student s) {
        Enrollment homeCohort = enrollmentService.currentHomeCohort(s.getId());
        if (homeCohort != null) {
            return cohortRepo.findById(homeCohort.getCohortId()).map(Cohort::getName).orElse(null);
        }
        return s.getClassId() == null ? null : classRepo.findById(s.getClassId()).map(SchoolClass::getName).orElse(null);
    }

    private List<Map<String, Object>> subjectsForClass(Long classId) {
        if (classId == null) return List.of();
        Map<Long, String> subjectNames = nameMap(subjectRepo.findAll(), Subject::getId, Subject::getName);
        Map<Long, String> teacherNames = nameMap(teacherRepo.findAll(), Teacher::getId,
                t -> t.getFirstName() + " " + t.getLastName());
        return classSubjectRepo.findByClassId(classId).stream().map(cs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subjectName", subjectNames.get(cs.getSubjectId()));
            row.put("teacherName", cs.getTeacherId() == null ? "-" : teacherNames.get(cs.getTeacherId()));
            return row;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> resultRows(Long studentId) {
        Map<Long, Assessment> assessments = assessmentRepo.findAll().stream()
                .collect(Collectors.toMap(Assessment::getId, Function.identity()));
        Map<Long, Long> csToSubject = classSubjectRepo.findAll().stream()
                .collect(Collectors.toMap(ClassSubject::getId, ClassSubject::getSubjectId));
        Map<Long, String> subjectNames = nameMap(subjectRepo.findAll(), Subject::getId, Subject::getName);

        return resultRepo.findByStudentId(studentId).stream().map(r -> {
            Assessment a = assessments.get(r.getAssessmentId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("assessment", a == null ? null : a.getTitle());
            row.put("term", a == null ? null : a.getTerm());
            String subjectName = (a != null) ? subjectNames.get(csToSubject.get(a.getClassSubjectId())) : null;
            row.put("subject", subjectName);
            row.put("score", r.getScore());
            row.put("maxScore", a == null ? null : a.getMaxScore());
            return row;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> attendanceSummary(Long studentId) {
        List<Attendance> rows = attendanceRepo.findByStudentIdOrderByOnDateDesc(studentId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", rows.size());
        m.put("present", rows.stream().filter(a -> "present".equals(a.getStatus())).count());
        m.put("absent", rows.stream().filter(a -> "absent".equals(a.getStatus())).count());
        m.put("late", rows.stream().filter(a -> "late".equals(a.getStatus())).count());
        m.put("recent", rows.stream().limit(10).collect(Collectors.toList()));
        return m;
    }

    private <T> Map<Long, String> nameMap(List<T> items, Function<T, Long> id, Function<T, String> name) {
        Map<Long, String> m = new HashMap<>();
        for (T it : items) m.put(id.apply(it), name.apply(it));
        return m;
    }
}
