package com.schoolhub.authservice.repository;

import com.schoolhub.authservice.model.TokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, Long> {
    boolean existsByTokenJti(String tokenJti);
}
