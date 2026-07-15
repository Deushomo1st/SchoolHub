package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByStudentIdOrderByOnDateDesc(Long studentId);
    Optional<Attendance> findByStudentIdAndOnDate(Long studentId, LocalDate onDate);
}
