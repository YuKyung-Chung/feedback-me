package com.jyk.feedbackme.repository;

import com.jyk.feedbackme.domain.AppUser;
import com.jyk.feedbackme.domain.CreditLedger;
import com.jyk.feedbackme.domain.CreditLedgerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
/**
 * FeedbackMe 백엔드의 CreditLedgerRepository 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.repository 계층의 책임을 담당합니다.
 */

public interface CreditLedgerRepository extends JpaRepository<CreditLedger, Long> {
    boolean existsByFeedbackHistoryIdAndType(Long feedbackHistoryId, CreditLedgerType type);

    List<CreditLedger> findTop20ByUserOrderByCreatedAtDesc(AppUser user);
}
