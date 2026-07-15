package com.schoolhub.authservice.repository;

import com.schoolhub.authservice.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    Optional<AppUser> findByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
}
