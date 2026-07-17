package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    // Self-scoped: the service filters by the caller's own user id, so every signed-in
    // role reads its own inbox (library approvals, forwarded events, ... land here).
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> list() { return ResponseEntity.ok(service.listMine()); }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(service.markRead(id));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> markReadPost(@PathVariable Long id) {
        return ResponseEntity.ok(service.markRead(id));
    }

    /** Programmatic: create a notification for a specific user (admin tooling only —
     *  regular flows notify via the service layer, and users must not spam each other). */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> req) {
        Long recipientUserId = req.get("recipientUserId") instanceof Number n ? n.longValue() : null;
        Long tenantId = req.get("tenantId") instanceof Number n ? n.longValue() : null;
        String type = (String) req.get("type");
        String title = (String) req.get("title");
        String body = (String) req.get("body");
        String linkType = (String) req.get("linkType");
        Long linkId = req.get("linkId") instanceof Number n ? n.longValue() : null;
        if (recipientUserId == null || type == null || title == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "recipientUserId, type, and title are required"));
        }
        service.notify(recipientUserId, type, title, body, linkType, linkId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true));
    }
}
