package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.StudentRequest;
import com.schoolhub.schoolservice.exception.ConflictException;
import com.schoolhub.schoolservice.model.ClassSubject;
import com.schoolhub.schoolservice.model.Cohort;
import com.schoolhub.schoolservice.model.CohortOffering;
import com.schoolhub.schoolservice.model.Enrollment;
import com.schoolhub.schoolservice.model.SchoolClass;
import com.schoolhub.schoolservice.model.Student;
import com.schoolhub.schoolservice.model.Teacher;
import com.schoolhub.schoolservice.repository.ClassSubjectRepository;
import com.schoolhub.schoolservice.repository.CohortOfferingRepository;
import com.schoolhub.schoolservice.repository.CohortRepository;
import com.schoolhub.schoolservice.repository.SchoolClassRepository;
import com.schoolhub.schoolservice.repository.StudentRepository;
import com.schoolhub.schoolservice.repository.TeacherRepository;
import com.schoolhub.schoolservice.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StudentService {

    private final StudentRepository repo;
    private final AccountProvisioning provisioning;
    private final TeacherRepository teacherRepo;
    private final SchoolClassRepository classRepo;
    private final ClassSubjectRepository classSubjectRepo;
    private final EnrollmentService enrollmentService;
    private final CohortRepository cohortRepo;
    private final CohortOfferingRepository cohortOfferingRepo;

    public StudentService(StudentRepository repo, AccountProvisioning provisioning,
                          TeacherRepository teacherRepo, SchoolClassRepository classRepo,
                          ClassSubjectRepository classSubjectRepo, EnrollmentService enrollmentService,
                          CohortRepository cohortRepo, CohortOfferingRepository cohortOfferingRepo) {
        this.repo = repo;
        this.provisioning = provisioning;
        this.teacherRepo = teacherRepo;
        this.classRepo = classRepo;
        this.classSubjectRepo = classSubjectRepo;
        this.enrollmentService = enrollmentService;
        this.cohortRepo = cohortRepo;
        this.cohortOfferingRepo = cohortOfferingRepo;
    }

    public List<Student> list() {
        if (seesAllStudents()) {
            return repo.findAllByOrderByLastNameAscFirstNameAsc();
        }
        Set<Long> classIds = teacherClassIds();
        Set<Long> cohortIds = teacherCohortIds();
        if (classIds.isEmpty() && cohortIds.isEmpty()) return List.of();
        return repo.findAllByOrderByLastNameAscFirstNameAsc().stream()
                .filter(s -> visibleToTeacher(s, classIds, cohortIds))
                .collect(Collectors.toList());
    }

    public Student get(Long id) {
        Student s = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Student not found: " + id));
        // A teacher may only open a student in a class/cohort they teach (don't reveal others even exist).
        if (!seesAllStudents() && !visibleToTeacher(s, teacherClassIds(), teacherCohortIds())) {
            throw new EntityNotFoundException("Student not found: " + id);
        }
        return s;
    }

    /** True if the legacy classId match hits, or (Enrollment-aware) the student's current
     *  home-Cohort is one this teacher heads or teaches an Offering in. */
    private boolean visibleToTeacher(Student s, Set<Long> classIds, Set<Long> cohortIds) {
        if (s.getClassId() != null && classIds.contains(s.getClassId())) return true;
        if (cohortIds.isEmpty()) return false;
        Enrollment homeCohort = enrollmentService.currentHomeCohort(s.getId());
        return homeCohort != null && cohortIds.contains(homeCohort.getCohortId());
    }

    private Set<Long> teacherCohortIds() {
        Long uid = TenantContext.getUserId();
        if (uid == null) return Set.of();
        Set<Long> ids = new HashSet<>();
        for (Cohort c : cohortRepo.findAll()) {
            if (uid.equals(c.getHeadUserId())) ids.add(c.getId());
        }
        Teacher t = teacherRepo.findByUserId(uid).orElse(null);
        if (t != null) {
            for (CohortOffering co : cohortOfferingRepo.findByTeacherId(t.getId())) ids.add(co.getCohortId());
        }
        return ids;
    }

    /** ADMIN / PRINCIPAL (ROLE_ADMIN) and BURSAR see the whole school; teachers are scoped to their classes. */
    private boolean seesAllStudents() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_BURSAR"));
    }

    private Set<Long> teacherClassIds() {
        Long uid = TenantContext.getUserId();
        if (uid == null) return Set.of();
        Teacher t = teacherRepo.findByUserId(uid).orElse(null);
        if (t == null) return Set.of();
        Set<Long> ids = new HashSet<>();
        for (SchoolClass c : classRepo.findAll()) {
            if (t.getId().equals(c.getClassTeacherId())) ids.add(c.getId());
        }
        for (ClassSubject cs : classSubjectRepo.findByTeacherId(t.getId())) ids.add(cs.getClassId());
        return ids;
    }

    @Transactional
    public Student create(StudentRequest req) {
        if (repo.existsByAdmissionNo(req.getAdmissionNo())) {
            throw new ConflictException("Admission number '" + req.getAdmissionNo() + "' already exists in this school");
        }
        Student s = new Student();
        apply(s, req);
        // Optional: give the student a login (admin sets the temp password).
        if (req.getLoginPassword() != null && !req.getLoginPassword().isBlank()) {
            s.setUserId(provisioning.createLogin(req.getEmail(), req.getLoginPassword(),
                    req.getFirstName(), req.getLastName(), req.getPhone(), "STUDENT"));
        }
        return save(s);
    }

    @Transactional
    public Student update(Long id, StudentRequest req) {
        Student s = get(id);
        apply(s, req);
        return save(s);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new EntityNotFoundException("Student not found: " + id);
        repo.deleteById(id);
    }

    private Student save(Student s) {
        try {
            return repo.save(s);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Admission number '" + s.getAdmissionNo() + "' already exists in this school");
        }
    }

    private void apply(Student s, StudentRequest req) {
        s.setAdmissionNo(req.getAdmissionNo());
        s.setFirstName(req.getFirstName());
        s.setLastName(req.getLastName());
        s.setGender(req.getGender());
        s.setDateOfBirth(req.getDateOfBirth());
        s.setEmail(req.getEmail());
        s.setPhone(req.getPhone());
        s.setClassId(req.getClassId());
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            s.setStatus(req.getStatus());
        }
    }
}
