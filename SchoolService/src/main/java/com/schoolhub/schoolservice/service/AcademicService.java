package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.*;
import com.schoolhub.schoolservice.exception.ConflictException;
import com.schoolhub.schoolservice.model.*;
import com.schoolhub.schoolservice.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Academic structure (subjects, classes, who-teaches-what) and records (assessments, results, attendance). */
@Service
public class AcademicService {

    private final SubjectRepository subjectRepo;
    private final SchoolClassRepository classRepo;
    private final ClassSubjectRepository classSubjectRepo;
    private final AssessmentRepository assessmentRepo;
    private final ResultRepository resultRepo;
    private final AttendanceRepository attendanceRepo;
    private final StudentRepository studentRepo;
    private final CohortOfferingRepository cohortOfferingRepo;
    private final TeacherRepository teacherRepo;
    private final ClassGroupRepository classGroupRepo;

    public AcademicService(SubjectRepository subjectRepo, SchoolClassRepository classRepo,
                           ClassSubjectRepository classSubjectRepo, AssessmentRepository assessmentRepo,
                           ResultRepository resultRepo, AttendanceRepository attendanceRepo,
                           StudentRepository studentRepo, CohortOfferingRepository cohortOfferingRepo,
                           TeacherRepository teacherRepo, ClassGroupRepository classGroupRepo) {
        this.subjectRepo = subjectRepo;
        this.classRepo = classRepo;
        this.classSubjectRepo = classSubjectRepo;
        this.assessmentRepo = assessmentRepo;
        this.resultRepo = resultRepo;
        this.attendanceRepo = attendanceRepo;
        this.studentRepo = studentRepo;
        this.cohortOfferingRepo = cohortOfferingRepo;
        this.teacherRepo = teacherRepo;
        this.classGroupRepo = classGroupRepo;
    }

    /** Who teaches this (new-model) assessment - for routing disputes/notifications. */
    public Long resolveTeacherUserIdForResult(Long resultId) {
        Result r = resultRepo.findById(resultId).orElseThrow(() -> new EntityNotFoundException("Result not found: " + resultId));
        Assessment a = getAssessment(r.getAssessmentId());
        if (a.getCohortOfferingId() == null) return null;
        CohortOffering co = cohortOfferingRepo.findById(a.getCohortOfferingId()).orElse(null);
        if (co == null || co.getTeacherId() == null) return null;
        return teacherRepo.findById(co.getTeacherId()).map(Teacher::getUserId).orElse(null);
    }

    // ---- Subjects ----
    public List<Subject> listSubjects() { return subjectRepo.findAllByOrderByNameAsc(); }

    @Transactional
    public Subject createSubject(SubjectReq req) {
        if (subjectRepo.existsByCode(req.code())) throw new ConflictException("Subject code '" + req.code() + "' already exists");
        Subject s = new Subject();
        s.setName(req.name());
        s.setCode(req.code());
        return subjectRepo.save(s);
    }

    @Transactional
    public void deleteSubject(Long id) {
        if (!subjectRepo.existsById(id)) throw new EntityNotFoundException("Subject not found: " + id);
        subjectRepo.deleteById(id);
    }

    // ---- Classes ----
    public List<SchoolClass> listClasses() { return classRepo.findAllByOrderByNameAsc(); }

    public SchoolClass getClass(Long id) {
        return classRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Class not found: " + id));
    }

    @Transactional
    public SchoolClass createClass(ClassReq req) {
        SchoolClass c = new SchoolClass();
        c.setName(req.name());
        c.setLevelLabel(req.levelLabel());
        c.setClassTeacherId(req.classTeacherId());
        try {
            return classRepo.save(c);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Class '" + req.name() + "' already exists");
        }
    }

    @Transactional
    public SchoolClass updateClass(Long id, ClassReq req) {
        SchoolClass c = getClass(id);
        c.setName(req.name());
        c.setLevelLabel(req.levelLabel());
        c.setClassTeacherId(req.classTeacherId());
        return classRepo.save(c);
    }

    @Transactional
    public void deleteClass(Long id) {
        if (!classRepo.existsById(id)) throw new EntityNotFoundException("Class not found: " + id);
        classRepo.deleteById(id);
    }

    // ---- Class-subject assignments (who teaches what, where) ----
    public List<ClassSubject> listClassSubjects(Long classId) {
        return classId != null ? classSubjectRepo.findByClassId(classId) : classSubjectRepo.findAll();
    }

    @Transactional
    public ClassSubject assignSubject(ClassSubjectReq req) {
        if (classSubjectRepo.existsByClassIdAndSubjectId(req.classId(), req.subjectId())) {
            throw new ConflictException("That subject is already assigned to that class");
        }
        ClassSubject cs = new ClassSubject();
        cs.setClassId(req.classId());
        cs.setSubjectId(req.subjectId());
        cs.setTeacherId(req.teacherId());
        return classSubjectRepo.save(cs);
    }

    @Transactional
    public void removeClassSubject(Long id) {
        if (!classSubjectRepo.existsById(id)) throw new EntityNotFoundException("Assignment not found: " + id);
        classSubjectRepo.deleteById(id);
    }

    // ---- Assessments + results ----
    public List<Assessment> listAssessments(Long classSubjectId) {
        return classSubjectId != null ? assessmentRepo.findByClassSubjectId(classSubjectId) : assessmentRepo.findAll();
    }

    public List<Assessment> listAssessmentsForCohortOffering(Long cohortOfferingId) {
        return assessmentRepo.findByCohortOfferingId(cohortOfferingId);
    }

    public Assessment getAssessment(Long id) {
        return assessmentRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Assessment not found: " + id));
    }

    /** classSubjectId (old model) or cohortOfferingId (new model) - at least one, same
     *  Java-side-only validation shape as Enrollment's cohort/offering/group check. */
    @Transactional
    public Assessment createAssessment(AssessmentReq req) {
        if (req.classSubjectId() == null && req.cohortOfferingId() == null) {
            throw new IllegalArgumentException("An assessment must target a classSubjectId or a cohortOfferingId");
        }
        // Old-model + groupId = a teacher-made class_group narrowing the assignment to its members;
        // it must belong to the same class as the classSubject it hangs off.
        if (req.classSubjectId() != null && req.groupId() != null) {
            ClassSubject cs = classSubjectRepo.findById(req.classSubjectId())
                    .orElseThrow(() -> new EntityNotFoundException("Class-subject not found: " + req.classSubjectId()));
            ClassGroup g = classGroupRepo.findById(req.groupId())
                    .orElseThrow(() -> new EntityNotFoundException("Group not found: " + req.groupId()));
            if (!g.getClassId().equals(cs.getClassId())) {
                throw new IllegalArgumentException("That group belongs to a different class");
            }
        }
        Assessment a = new Assessment();
        a.setClassSubjectId(req.classSubjectId());
        a.setTitle(req.title());
        if (req.term() != null) a.setTerm(req.term());
        if (req.maxScore() != null) a.setMaxScore(req.maxScore());
        if (req.assessedOn() != null) a.setAssessedOn(req.assessedOn());
        a.setCohortOfferingId(req.cohortOfferingId());
        a.setGroupId(req.groupId());
        a.setSessionId(req.sessionId());
        a.setWeight(req.weight());
        if (req.passMarkPercent() != null) a.setPassMarkPercent(req.passMarkPercent());
        return assessmentRepo.save(a);
    }

    /** The teacher's release gate - unpublished assessments/results stay invisible to students. */
    @Transactional
    public Assessment setPublished(Long id, boolean published) {
        Assessment a = getAssessment(id);
        a.setPublished(published);
        return assessmentRepo.save(a);
    }

    public List<Result> listResults(Long assessmentId) { return resultRepo.findByAssessmentId(assessmentId); }

    /**
     * Record or overwrite a student's score for an assessment. A resit keeps the raw score as
     * submitted but tracks a flat penalty separately (applied at read/transcript time), so what
     * the student actually scored stays visible rather than being destructively adjusted.
     */
    @Transactional
    public Result recordResult(ResultReq req) {
        Result r = resultRepo.findByAssessmentIdAndStudentId(req.assessmentId(), req.studentId())
                .orElseGet(Result::new);
        boolean isResit = Boolean.TRUE.equals(req.isResit());
        r.setAssessmentId(req.assessmentId());
        r.setStudentId(req.studentId());
        r.setScore(req.score());
        r.setResit(isResit);
        r.setPenalty(isResit ? java.math.BigDecimal.valueOf(3) : java.math.BigDecimal.ZERO);
        return resultRepo.save(r);
    }

    /** On-demand (never stored/frozen) - one Cohort-Offering's published assessments for one
     *  student, with PASS/FAIL derived from each assessment's passMarkPercent at read time. */
    public java.util.Map<String, Object> transcript(Long studentId, Long cohortOfferingId) {
        List<Assessment> assessments = assessmentRepo.findByCohortOfferingId(cohortOfferingId).stream()
                .filter(Assessment::isPublished).toList();
        List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        double totalPercent = 0;
        int counted = 0;
        for (Assessment a : assessments) {
            Result r = resultRepo.findByAssessmentIdAndStudentId(a.getId(), studentId).orElse(null);
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("assessment", a.getTitle());
            row.put("term", a.getTerm());
            row.put("maxScore", a.getMaxScore());
            row.put("weight", a.getWeight());
            if (r != null) {
                double effective = Math.max(0, r.getScore() - r.getPenalty().doubleValue());
                double percent = a.getMaxScore() == 0 ? 0 : (effective / a.getMaxScore()) * 100;
                row.put("score", r.getScore());
                row.put("penalty", r.getPenalty());
                row.put("percent", percent);
                row.put("passFail", percent >= a.getPassMarkPercent().doubleValue() ? "PASS" : "FAIL");
                totalPercent += percent;
                counted++;
            } else {
                row.put("score", null);
                row.put("percent", null);
                row.put("passFail", null);
            }
            rows.add(row);
        }
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("cohortOfferingId", cohortOfferingId);
        out.put("studentId", studentId);
        out.put("assessments", rows);
        out.put("averagePercent", counted == 0 ? null : totalPercent / counted);
        return out;
    }

    // ---- Attendance ----
    public List<Attendance> listAttendance(Long studentId) {
        return attendanceRepo.findByStudentIdOrderByOnDateDesc(studentId);
    }

    /** Mark (or correct) a student's attendance for a day. */
    @Transactional
    public Attendance markAttendance(AttendanceReq req) {
        if (!studentRepo.existsById(req.studentId())) throw new EntityNotFoundException("Student not found: " + req.studentId());
        java.time.LocalDate day = req.onDate() != null ? req.onDate() : java.time.LocalDate.now();
        Attendance a = attendanceRepo.findByStudentIdAndOnDate(req.studentId(), day).orElseGet(Attendance::new);
        a.setStudentId(req.studentId());
        a.setClassId(req.classId());
        a.setOnDate(day);
        a.setStatus(req.status());
        return attendanceRepo.save(a);
    }
}
