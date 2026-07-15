package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One row per redemption attempt. For a targeted invite (GUARDIAN_LINK) this is created
 *  immediately alongside the Invite itself, since the redeemer is already known. */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "invite_redemption", schema = "platform")
public class InviteRedemption {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invite_id", nullable = false)
    private Long inviteId;

    @Column(name = "redeemed_by_user_id", nullable = false)
    private Long redeemedByUserId;

    @Column(nullable = false)
    private String status = "pending_confirmation";

    @Column(name = "confirmed_by_user_id")
    private Long confirmedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @PrePersist void pre() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getInviteId() { return inviteId; }
    public void setInviteId(Long inviteId) { this.inviteId = inviteId; }
    public Long getRedeemedByUserId() { return redeemedByUserId; }
    public void setRedeemedByUserId(Long redeemedByUserId) { this.redeemedByUserId = redeemedByUserId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getConfirmedByUserId() { return confirmedByUserId; }
    public void setConfirmedByUserId(Long confirmedByUserId) { this.confirmedByUserId = confirmedByUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
}
