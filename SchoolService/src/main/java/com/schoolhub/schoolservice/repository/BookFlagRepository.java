package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.BookFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookFlagRepository extends JpaRepository<BookFlag, Long> {
    List<BookFlag> findByBookId(Long bookId);
    List<BookFlag> findByEscalatedTrue();
    List<BookFlag> findByAdminDecisionIsNullAndEscalatedTrue();
}
