package com.schoolhub.tenantservice.repository;

import com.schoolhub.tenantservice.model.ActivityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, Long> {

    @Query("SELECT e FROM ActivityEvent e WHERE e.createdAt > :since ORDER BY e.createdAt DESC")
    List<ActivityEvent> findRecent(LocalDateTime since);
}
