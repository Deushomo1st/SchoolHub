package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.GuardianReq;
import com.schoolhub.schoolservice.dto.Requests.TeacherReq;
import com.schoolhub.schoolservice.exception.ConflictException;
import com.schoolhub.schoolservice.model.*;
import com.schoolhub.schoolservice.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Teachers and guardians - the school's people (students have their own service). */
@Service
public class PeopleService {

    private final TeacherRepository teacherRepo;
    private final GuardianRepository guardianRepo;
    private final StudentGuardianRepository linkRepo;
    private final StudentRepository studentRepo;
    private final AppUserRepository appUserRepo;
    private final AccountProvisioning provisioning;

    public PeopleService(TeacherRepository teacherRepo, GuardianRepository guardianRepo,
                         StudentGuardianRepository linkRepo, StudentRepository studentRepo,
                         AppUserRepository appUserRepo, AccountProvisioning provisioning) {
        this.teacherRepo = teacherRepo;
        this.guardianRepo = guardianRepo;
        this.linkRepo = linkRepo;
        this.studentRepo = studentRepo;
        this.appUserRepo = appUserRepo;
        this.provisioning = provisioning;
    }

    /** Public handle lookup (e.g. a guardian finding a ward before linking) - only the minimal,
     *  non-sensitive fields, never the AppUser entity itself (it carries the password hash). */
    public Map<String, Object> searchByHandle(String handle) {
        AppUser u = appUserRepo.findByUsernameIgnoreCase(handle)
                .orElseThrow(() -> new EntityNotFoundException("No account with that handle"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getId());
        m.put("handle", u.getUsername());
        m.put("firstName", u.getFirstName());
        m.put("lastName", u.getLastName());
        return m;
    }

    // ---- Teachers ----
    public List<Teacher> listTeachers() { return teacherRepo.findAllByOrderByLastNameAscFirstNameAsc(); }

    public Teacher getTeacher(Long id) {
        return teacherRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Teacher not found: " + id));
    }

    @Transactional
    public Teacher createTeacher(TeacherReq req) {
        if (teacherRepo.existsByStaffNo(req.staffNo())) {
            throw new ConflictException("Staff number '" + req.staffNo() + "' already exists");
        }
        Teacher t = new Teacher();
        t.setStaffNo(req.staffNo());
        t.setFirstName(req.firstName());
        t.setLastName(req.lastName());
        t.setEmail(req.email());
        t.setPhone(req.phone());
        if (req.status() != null && !req.status().isBlank()) t.setStatus(req.status());
        if (req.loginPassword() != null && !req.loginPassword().isBlank()) {
            t.setUserId(provisioning.createLogin(req.email(), req.loginPassword(),
                    req.firstName(), req.lastName(), req.phone(), "TEACHER"));
        }
        return teacherRepo.save(t);
    }

    @Transactional
    public Teacher updateTeacher(Long id, TeacherReq req) {
        Teacher t = getTeacher(id);
        t.setStaffNo(req.staffNo());
        t.setFirstName(req.firstName());
        t.setLastName(req.lastName());
        t.setEmail(req.email());
        t.setPhone(req.phone());
        if (req.status() != null && !req.status().isBlank()) t.setStatus(req.status());
        return teacherRepo.save(t);
    }

    @Transactional
    public void deleteTeacher(Long id) {
        if (!teacherRepo.existsById(id)) throw new EntityNotFoundException("Teacher not found: " + id);
        teacherRepo.deleteById(id);
    }

    // ---- Guardians ----
    public List<Guardian> listGuardians() { return guardianRepo.findAllByOrderByLastNameAscFirstNameAsc(); }

    public Guardian getGuardian(Long id) {
        return guardianRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Guardian not found: " + id));
    }

    @Transactional
    public Guardian createGuardian(GuardianReq req) {
        Guardian g = new Guardian();
        g.setFirstName(req.firstName());
        g.setLastName(req.lastName());
        g.setEmail(req.email());
        g.setPhone(req.phone());
        if (req.loginPassword() != null && !req.loginPassword().isBlank()) {
            g.setUserId(provisioning.createLogin(req.email(), req.loginPassword(),
                    req.firstName(), req.lastName(), req.phone(), "PARENT"));
        }
        g = guardianRepo.save(g);
        if (req.studentIds() != null) {
            for (Long studentId : req.studentIds()) {
                if (!studentRepo.existsById(studentId)) {
                    throw new EntityNotFoundException("Student not found: " + studentId);
                }
                StudentGuardian sg = new StudentGuardian();
                sg.setGuardianId(g.getId());
                sg.setStudentId(studentId);
                sg.setRelationship(req.relationship());
                linkRepo.save(sg);
            }
        }
        return g;
    }

    @Transactional
    public void deleteGuardian(Long id) {
        if (!guardianRepo.existsById(id)) throw new EntityNotFoundException("Guardian not found: " + id);
        guardianRepo.deleteById(id);
    }

    // ---- Guardian-initiated child linking (applied by WorkflowService after admin confirms) ----

    public Guardian guardianForUser(Long userId) {
        return guardianRepo.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("No guardian profile is linked to your account"));
    }

    public Student studentByHandle(String handle) {
        AppUser u = appUserRepo.findByUsernameIgnoreCase(handle)
                .orElseThrow(() -> new EntityNotFoundException("No account with that handle"));
        return studentRepo.findByUserId(u.getId())
                .orElseThrow(() -> new EntityNotFoundException("That handle doesn't belong to a student"));
    }

    @Transactional
    public StudentGuardian linkGuardianChild(Long guardianId, Long studentId, String relationship) {
        if (!studentRepo.existsById(studentId)) throw new EntityNotFoundException("Student not found: " + studentId);
        getGuardian(guardianId);
        if (linkRepo.existsByStudentIdAndGuardianId(studentId, guardianId)) {
            throw new ConflictException("That child is already linked to this guardian");
        }
        StudentGuardian sg = new StudentGuardian();
        sg.setGuardianId(guardianId);
        sg.setStudentId(studentId);
        sg.setRelationship(relationship);
        return linkRepo.save(sg);
    }
}
