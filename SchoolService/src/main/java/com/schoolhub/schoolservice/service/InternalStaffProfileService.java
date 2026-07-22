package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.exception.ConflictException;
import com.schoolhub.schoolservice.model.*;
import com.schoolhub.schoolservice.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Internal (service-to-service) profile creation — called by TenantService when an
 * admin approves a staff signup. Every profile row is linked to the auth user by
 * {@code userId}.
 */
@Service
public class InternalStaffProfileService {

    private final TeacherRepository teacherRepo;
    private final BursarRepository bursarRepo;
    private final LibraryStaffRepository libraryStaffRepo;

    public InternalStaffProfileService(TeacherRepository teacherRepo,
                                       BursarRepository bursarRepo,
                                       LibraryStaffRepository libraryStaffRepo) {
        this.teacherRepo = teacherRepo;
        this.bursarRepo = bursarRepo;
        this.libraryStaffRepo = libraryStaffRepo;
    }

    @Transactional
    public Teacher createTeacher(Map<String, String> body) {
        String staffNo = require(body, "staffNo");
        if (teacherRepo.existsByStaffNo(staffNo)) {
            throw new ConflictException("Staff number '" + staffNo + "' already exists");
        }
        Teacher t = new Teacher();
        t.setStaffNo(staffNo);
        t.setFirstName(require(body, "firstName"));
        t.setLastName(require(body, "lastName"));
        t.setEmail(body.get("email"));
        t.setPhone(body.get("phone"));
        t.setUserId(parseLong(body, "userId"));
        t.setStatus("active");
        return teacherRepo.save(t);
    }

    @Transactional
    public Bursar createBursar(Map<String, String> body) {
        String staffNo = require(body, "staffNo");
        if (bursarRepo.existsByStaffNo(staffNo)) {
            throw new ConflictException("Staff number '" + staffNo + "' already exists");
        }
        Bursar b = new Bursar();
        b.setStaffNo(staffNo);
        b.setFirstName(require(body, "firstName"));
        b.setLastName(require(body, "lastName"));
        b.setEmail(body.get("email"));
        b.setPhone(body.get("phone"));
        b.setUserId(parseLong(body, "userId"));
        b.setStatus("active");
        return bursarRepo.save(b);
    }

    @Transactional
    public LibraryStaff createLibrarian(Map<String, String> body) {
        LibraryStaff s = new LibraryStaff();
        s.setUserId(parseLong(body, "userId"));
        String staffNo = body.get("staffNo");
        if (staffNo != null && !staffNo.isBlank()) s.setStaffNo(staffNo.trim());
        s.setFirstName(body.get("firstName"));
        s.setLastName(body.get("lastName"));
        s.setEmail(body.get("email"));
        s.setPhone(body.get("phone"));
        s.setStatus("active");
        return libraryStaffRepo.save(s);
    }

    private static String require(Map<String, String> body, String key) {
        String v = body.get(key);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing required field: " + key);
        return v.trim();
    }

    private static Long parseLong(Map<String, String> body, String key) {
        String v = body.get(key);
        if (v == null) throw new IllegalArgumentException("Missing required field: " + key);
        return Long.parseLong(v.trim());
    }
}
