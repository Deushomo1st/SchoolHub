package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.service.WorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workflow-protests")
public class WorkflowProtestController {

    private final WorkflowService service;

    public WorkflowProtestController(WorkflowService service) {
        this.service = service;
    }

    // Seconding cancels the action being protested; dismissing clears the way for it to proceed.
    @PostMapping("/{id}/second")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> second(@PathVariable Long id) {
        service.secondProtest(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> dismiss(@PathVariable Long id) {
        service.dismissProtest(id);
        return ResponseEntity.ok().build();
    }
}
