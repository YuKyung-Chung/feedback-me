package com.jyk.feedbackme.repository;

import com.jyk.feedbackme.domain.AppUser;
import com.jyk.feedbackme.domain.UserCreditBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
/**
 * FeedbackMe 백엔드의 UserCreditBalanceRepository 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.repository 계층의 책임을 담당합니다.
 */

public interface UserCreditBalanceRepository extends JpaRepository<UserCreditBalance, Long> {
    Optional<UserCreditBalance> findByUser(AppUser user);
}
