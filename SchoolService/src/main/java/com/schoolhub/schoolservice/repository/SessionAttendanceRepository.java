package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.SessionAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionAttendanceRepository extends JpaRepository<SessionAttendance, Long> {
    List<SessionAttendance> findBySessionId(Long sessionId);
    List<SessionAttendance> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    Optional<SessionAttendance> findBySessionIdAndStudentId(Long sessionId, Long studentId);
}
