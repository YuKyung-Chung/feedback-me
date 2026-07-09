package com.jyk.feedbackme.repository;

import com.jyk.feedbackme.domain.AppUser;
import com.jyk.feedbackme.domain.CreditLedger;
import com.jyk.feedbackme.domain.CreditLedgerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditLedgerRepository extends JpaRepository<CreditLedger, Long> {
    boolean existsByFeedbackHistoryIdAndType(Long feedbackHistoryId, CreditLedgerType type);

    List<CreditLedger> findTop20ByUserOrderByCreatedAtDesc(AppUser user);
}
