package com.jyk.feedbackme.dto;

public record PaymentConfirmRequest(
        String paymentKey,
        String orderId,
        int amount
) {
}
