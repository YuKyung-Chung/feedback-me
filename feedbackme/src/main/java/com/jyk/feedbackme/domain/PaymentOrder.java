package com.jyk.feedbackme.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
@Table(name = "payment_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/**
 * FeedbackMe 백엔드의 PaymentOrder 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.domain 계층의 책임을 담당합니다.
 */
public class PaymentOrder {

    @Id
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    private String productCode;
    private String orderName;
    private int amount;
    private int creditAmount;
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime failedAt;

    @Builder
    public PaymentOrder(String orderId, AppUser user, String productCode, String orderName, int amount, int creditAmount) {
        this.orderId = orderId;
        this.user = user;
        this.productCode = productCode;
        this.orderName = orderName;
        this.amount = amount;
        this.creditAmount = creditAmount;
        this.status = PaymentStatus.READY;
        this.requestedAt = LocalDateTime.now();
    }

    public void markPaid(String paymentKey) {
        this.paymentKey = paymentKey;
        this.status = PaymentStatus.PAID;
        this.approvedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
        this.failedAt = LocalDateTime.now();
    }
}
