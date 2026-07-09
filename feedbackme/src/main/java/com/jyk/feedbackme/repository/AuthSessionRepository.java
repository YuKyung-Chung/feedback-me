package com.jyk.feedbackme.repository;

import com.jyk.feedbackme.domain.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findByTokenHashAndRevokedAtIsNull(String tokenHash);
}
