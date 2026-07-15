package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.EventReq;
import com.schoolhub.schoolservice.model.CalendarEvent;
import com.schoolhub.schoolservice.repository.CalendarEventRepository;
import com.schoolhub.schoolservice.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** School calendar: events, announcements, holidays, exams. */
@Service
public class EventService {

    private final CalendarEventRepository repo;

    public EventService(CalendarEventRepository repo) {
        this.repo = repo;
    }

    public List<CalendarEvent> list() { return repo.findAllByOrderByStartDateDesc(); }

    @Transactional
    public CalendarEvent create(EventReq req) {
        CalendarEvent e = new CalendarEvent();
        e.setTitle(req.title());
        e.setDescription(req.description());
        if (req.eventType() != null) e.setEventType(req.eventType());
        if (req.audience() != null) e.setAudience(req.audience());
        e.setStartDate(req.startDate());
        e.setEndDate(req.endDate());
        e.setCreatedBy(TenantContext.getUserId());
        return repo.save(e);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new EntityNotFoundException("Event not found: " + id);
        repo.deleteById(id);
    }
}
