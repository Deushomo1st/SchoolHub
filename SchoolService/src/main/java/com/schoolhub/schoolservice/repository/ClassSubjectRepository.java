package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.ClassSubject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassSubjectRepository extends JpaRepository<ClassSubject, Long> {
    List<ClassSubject> findByClassId(Long classId);
    List<ClassSubject> findByTeacherId(Long teacherId);
    boolean existsByClassIdAndSubjectId(Long classId, Long subjectId);
}
