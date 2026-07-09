package com.jyk.feedbackme.dto;

public record PaymentProduct(
        String code,
        String name,
        int credits,
        int amount
) {
}
