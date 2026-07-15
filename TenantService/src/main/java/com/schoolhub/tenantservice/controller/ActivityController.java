package com.schoolhub.tenantservice.controller;

import com.schoolhub.tenantservice.model.ActivityEvent;
import com.schoolhub.tenantservice.repository.ActivityEventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/activity")
public class ActivityController {

    private final ActivityEventRepository repo;

    public ActivityController(ActivityEventRepository repo) { this.repo = repo; }

    @PostMapping("/ping")
    public ResponseEntity<?> ping(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") instanceof Number n ? n.longValue() : null;
        String action = (String) body.getOrDefault("action", "unknown");
        double size = body.get("size") instanceof Number n ? n.doubleValue() : 1.0;
        repo.save(new ActivityEvent(userId, action, size));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/feed")
    public ResponseEntity<?> feed() {
        List<ActivityEvent> events = repo.findRecent(LocalDateTime.now().minusMinutes(2));
        List<Map<String, Object>> out = new ArrayList<>();
        for (ActivityEvent e : events) {
            Map<String, Object> m = new HashMap<>();
            m.put("ts", e.getCreatedAt().toString());
            m.put("action", e.getAction());
            m.put("size", e.getSize());
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }
}
