package com.jyk.feedbackme.repository;

import com.jyk.feedbackme.domain.AppUser;
import com.jyk.feedbackme.domain.FeedbackHistory;
import com.jyk.feedbackme.domain.FeedbackStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackHistoryRepository extends JpaRepository<FeedbackHistory, Long> {
    List<FeedbackHistory> findTop20ByUserAndStatusOrderByCreatedAtDesc(AppUser user, FeedbackStatus status);

    Optional<FeedbackHistory> findByIdAndUser(Long id, AppUser user);
}
