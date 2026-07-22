package com.jyk.feedbackme.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "credit_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/**
 * FeedbackMe 백엔드의 CreditLedger 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.domain 계층의 책임을 담당합니다.
 */
public class CreditLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    private CreditLedgerType type;

    private int amount;
    private Long feedbackHistoryId;
    private String paymentOrderId;
    private String memo;
    private LocalDateTime createdAt;

    @Builder
    public CreditLedger(AppUser user, CreditLedgerType type, int amount, Long feedbackHistoryId, String paymentOrderId, String memo) {
        this.user = user;
        this.type = type;
        this.amount = amount;
        this.feedbackHistoryId = feedbackHistoryId;
        this.paymentOrderId = paymentOrderId;
        this.memo = memo;
        this.createdAt = LocalDateTime.now();
    }
}
