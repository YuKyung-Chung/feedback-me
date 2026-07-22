package com.jyk.feedbackme.repository;

import com.jyk.feedbackme.domain.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
/**
 * FeedbackMe 백엔드의 AuthSessionRepository 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.repository 계층의 책임을 담당합니다.
 */

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findByTokenHashAndRevokedAtIsNull(String tokenHash);
}
