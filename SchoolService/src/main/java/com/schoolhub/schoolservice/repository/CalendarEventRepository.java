package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {
    List<CalendarEvent> findAllByOrderByStartDateDesc();
}
