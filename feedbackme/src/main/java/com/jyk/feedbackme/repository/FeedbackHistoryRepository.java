package com.jyk.feedbackme.repository;

import com.jyk.feedbackme.domain.AppUser;
import com.jyk.feedbackme.domain.FeedbackHistory;
import com.jyk.feedbackme.domain.FeedbackStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
/**
 * FeedbackMe 백엔드의 FeedbackHistoryRepository 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.repository 계층의 책임을 담당합니다.
 */
public interface FeedbackHistoryRepository extends JpaRepository<FeedbackHistory, Long> {
    List<FeedbackHistory> findTop20ByUserAndStatusOrderByCreatedAtDesc(AppUser user, FeedbackStatus status);

    Optional<FeedbackHistory> findByIdAndUser(Long id, AppUser user);
}
