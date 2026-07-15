package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.model.Notification;
import com.schoolhub.schoolservice.repository.NotificationRepository;
import com.schoolhub.schoolservice.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Green-field push notifications - polling only (no WebSocket dependency in this codebase),
 *  same "thin wrapper around repository.save()" shape as AuditRecorder. */
@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    public void notify(Long recipientUserId, String type, String title, String body, String linkType, Long linkId) {
        Notification n = new Notification();
        n.setRecipientUserId(recipientUserId);
        n.setTenantId(TenantContext.getTenantId());
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLinkType(linkType);
        n.setLinkId(linkId);
        repo.save(n);
    }

    public List<Notification> listMine() {
        return repo.findByRecipientUserIdOrderByCreatedAtDesc(TenantContext.getUserId());
    }

    @Transactional
    public Notification markRead(Long id) {
        Notification n = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Notification not found: " + id));
        if (!n.getRecipientUserId().equals(TenantContext.getUserId())) {
            throw new AccessDeniedException("That notification is not yours");
        }
        n.setReadAt(LocalDateTime.now());
        return repo.save(n);
    }
}
