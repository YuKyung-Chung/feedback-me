package com.jyk.feedbackme.dto;
/**
 * FeedbackMe 백엔드의 PaymentCreateRequest 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.dto 계층의 책임을 담당합니다.
 */

public record PaymentCreateRequest(
        String productCode
) {
}
