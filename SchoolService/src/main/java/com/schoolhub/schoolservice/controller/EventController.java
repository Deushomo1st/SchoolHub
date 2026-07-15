package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.EventReq;
import com.schoolhub.schoolservice.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService events;

    public EventController(EventService events) {
        this.events = events;
    }

    @GetMapping
    public ResponseEntity<?> list() { return ResponseEntity.ok(events.list()); }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> create(@Valid @RequestBody EventReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(events.create(req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        events.delete(id);
        return ResponseEntity.ok().build();
    }
}
