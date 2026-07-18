package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.ClassGroupReq;
import com.schoolhub.schoolservice.exception.ConflictException;
import com.schoolhub.schoolservice.model.ClassGroup;
import com.schoolhub.schoolservice.model.ClassGroupMember;
import com.schoolhub.schoolservice.model.ClassSubject;
import com.schoolhub.schoolservice.model.Student;
import com.schoolhub.schoolservice.model.Teacher;
import com.schoolhub.schoolservice.repository.ClassGroupMemberRepository;
import com.schoolhub.schoolservice.repository.ClassGroupRepository;
import com.schoolhub.schoolservice.repository.ClassSubjectRepository;
import com.schoolhub.schoolservice.repository.SchoolClassRepository;
import com.schoolhub.schoolservice.repository.StudentRepository;
import com.schoolhub.schoolservice.repository.TeacherRepository;
import com.schoolhub.schoolservice.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Teacher-made sub-groups inside a classic SchoolClass. A teacher may only touch groups in
 *  classes they actually teach (class teacher or subject assignment); Admins touch any. */
@Service
public class ClassGroupService {

    private final ClassGroupRepository groupRepo;
    private final ClassGroupMemberRepository memberRepo;
    private final SchoolClassRepository classRepo;
    private final StudentRepository studentRepo;
    private final TeacherRepository teacherRepo;
    private final ClassSubjectRepository classSubjectRepo;

    public ClassGroupService(ClassGroupRepository groupRepo, ClassGroupMemberRepository memberRepo,
                             SchoolClassRepository classRepo, StudentRepository studentRepo,
                             TeacherRepository teacherRepo, ClassSubjectRepository classSubjectRepo) {
        this.groupRepo = groupRepo;
        this.memberRepo = memberRepo;
        this.classRepo = classRepo;
        this.studentRepo = studentRepo;
        this.teacherRepo = teacherRepo;
        this.classSubjectRepo = classSubjectRepo;
    }

    /** Groups of one class with their members joined in, so the UI stays thin. */
    public List<Map<String, Object>> list(Long classId) {
        requireClass(classId);
        List<ClassGroup> groups = groupRepo.findByClassIdOrderByNameAsc(classId);
        if (groups.isEmpty()) return List.of();

        Map<Long, List<Long>> membersByGroup = memberRepo
                .findByGroupIdIn(groups.stream().map(ClassGroup::getId).toList()).stream()
                .collect(Collectors.groupingBy(ClassGroupMember::getGroupId,
                        Collectors.mapping(ClassGroupMember::getStudentId, Collectors.toList())));
        Map<Long, String> studentNames = studentRepo.findByClassId(classId).stream()
                .collect(Collectors.toMap(Student::getId, s -> s.getLastName() + ", " + s.getFirstName()));

        return groups.stream().map(g -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", g.getId());
            row.put("classId", g.getClassId());
            row.put("name", g.getName());
            row.put("createdBy", g.getCreatedBy());
            List<Long> ids = membersByGroup.getOrDefault(g.getId(), List.of());
            row.put("members", ids.stream().map(id -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("studentId", id);
                m.put("name", studentNames.getOrDefault(id, "#" + id));
                return m;
            }).toList());
            return row;
        }).toList();
    }

    @Transactional
    public Map<String, Object> create(Long classId, ClassGroupReq req) {
        requireClass(classId);
        requireTeachesClass(classId);
        if (groupRepo.existsByClassIdAndNameIgnoreCase(classId, req.name().trim())) {
            throw new ConflictException("A group named '" + req.name().trim() + "' already exists in that class");
        }
        ClassGroup g = new ClassGroup();
        g.setClassId(classId);
        g.setName(req.name().trim());
        g.setCreatedBy(TenantContext.getUserId());
        g = groupRepo.save(g);
        saveMembers(g, req.studentIds());
        return single(g);
    }

    @Transactional
    public Map<String, Object> update(Long groupId, ClassGroupReq req) {
        ClassGroup g = get(groupId);
        requireTeachesClass(g.getClassId());
        g.setName(req.name().trim());
        g = groupRepo.save(g);
        memberRepo.deleteByGroupId(g.getId());
        // Hibernate flushes INSERTs before DELETEs; force the delete out first or re-added
        // members trip the (group_id, student_id) unique constraint.
        memberRepo.flush();
        saveMembers(g, req.studentIds());
        return single(g);
    }

    @Transactional
    public void delete(Long groupId) {
        ClassGroup g = get(groupId);
        requireTeachesClass(g.getClassId());
        memberRepo.deleteByGroupId(groupId);
        groupRepo.deleteById(groupId);
    }

    // ---- helpers ----

    private void saveMembers(ClassGroup g, List<Long> studentIds) {
        if (studentIds == null) return;
        Set<Long> inClass = studentRepo.findByClassId(g.getClassId()).stream()
                .map(Student::getId).collect(Collectors.toSet());
        for (Long studentId : studentIds.stream().distinct().toList()) {
            if (!inClass.contains(studentId)) {
                throw new EntityNotFoundException("Student #" + studentId + " is not in that class");
            }
            ClassGroupMember m = new ClassGroupMember();
            m.setGroupId(g.getId());
            m.setStudentId(studentId);
            memberRepo.save(m);
        }
    }

    private ClassGroup get(Long id) {
        return groupRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Group not found: " + id));
    }

    private void requireClass(Long classId) {
        if (!classRepo.existsById(classId)) throw new EntityNotFoundException("Class not found: " + classId);
    }

    /** Admins pass; a teacher must be the class teacher or hold a subject assignment in it. */
    private void requireTeachesClass(Long classId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;
        Teacher t = teacherRepo.findByUserId(TenantContext.getUserId())
                .orElseThrow(() -> new AccessDeniedException("No teacher profile is linked to your account"));
        boolean classTeacher = classRepo.findById(classId)
                .map(c -> t.getId().equals(c.getClassTeacherId())).orElse(false);
        boolean assigned = classSubjectRepo.findByTeacherId(t.getId()).stream()
                .map(ClassSubject::getClassId).anyMatch(classId::equals);
        if (!classTeacher && !assigned) {
            throw new AccessDeniedException("You don't teach that class");
        }
    }

    private Map<String, Object> single(ClassGroup g) {
        return list(g.getClassId()).stream()
                .filter(row -> g.getId().equals(row.get("id"))).findFirst()
                .orElseThrow(() -> new IllegalStateException("Group vanished mid-save"));
    }
}
