package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.Credential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CredentialRepository extends JpaRepository<Credential, Long> {
    List<Credential> findByPersonUserIdOrderByIssuedAtDesc(Long personUserId);
}
