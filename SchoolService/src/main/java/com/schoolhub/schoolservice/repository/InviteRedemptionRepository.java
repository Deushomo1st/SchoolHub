package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.InviteRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InviteRedemptionRepository extends JpaRepository<InviteRedemption, Long> {
    List<InviteRedemption> findByStatus(String status);
    List<InviteRedemption> findByInviteId(Long inviteId);
}
