package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.model.Role;
import com.schoolhub.schoolservice.repository.AppUserRepository;
import com.schoolhub.schoolservice.repository.RoleRepository;
import com.schoolhub.schoolservice.service.NotificationService;
import com.schoolhub.schoolservice.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * Generic "flag an issue" — anything on any page can be flagged by any signed-in member.
 * ponytail: a flag is a notification to every school Admin, not its own table; give it one
 * (with states/decisions) if flags ever need triage beyond the bell.
 */
@RestController
@RequestMapping("/api/v1/flags")
public class FlagController {

    private static final Set<String> TARGETS =
            Set.of("event", "result", "attendance", "invoice", "profile", "group", "book", "other");

    public record FlagReq(@NotBlank String targetType, @Size(max = 120) String targetLabel,
                          @NotBlank @Size(max = 500) String comment) {}

    private final AppUserRepository userRepo;
    private final RoleRepository roleRepo;
    private final NotificationService notifications;

    public FlagController(AppUserRepository userRepo, RoleRepository roleRepo, NotificationService notifications) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.notifications = notifications;
    }

    @PostMapping
    public ResponseEntity<?> raise(@Valid @RequestBody FlagReq req) {
        String target = req.targetType().trim().toLowerCase();
        if (!TARGETS.contains(target)) {
            throw new IllegalArgumentException("Flag target must be one of " + TARGETS);
        }
        String who = userRepo.findById(TenantContext.getUserId())
                .map(u -> u.getFirstName() + " " + u.getLastName()).orElse("Someone");
        String label = req.targetLabel() == null ? target : req.targetLabel().trim();
        Role admin = roleRepo.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing"));
        for (var u : userRepo.findByRoleIdAndTenantId(admin.getId(), TenantContext.getTenantId())) {
            notifications.notify(u.getId(), "flag", "🚩 " + who + " flagged: " + label,
                    req.comment().trim(), null, null);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "flagged"));
    }
}
