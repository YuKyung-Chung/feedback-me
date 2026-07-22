package com.jyk.feedbackme.repository;

import com.jyk.feedbackme.domain.AppUser;
import com.jyk.feedbackme.domain.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
/**
 * FeedbackMe 백엔드의 PaymentOrderRepository 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.repository 계층의 책임을 담당합니다.
 */

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {
    List<PaymentOrder> findTop20ByUserOrderByRequestedAtDesc(AppUser user);
}
