package com.jyk.feedbackme.repository;

import com.jyk.feedbackme.domain.AppUser;
import com.jyk.feedbackme.domain.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {
    List<PaymentOrder> findTop20ByUserOrderByRequestedAtDesc(AppUser user);
}
