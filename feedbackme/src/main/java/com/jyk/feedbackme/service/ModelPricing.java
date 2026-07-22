package com.jyk.feedbackme.service;

import org.springframework.stereotype.Component;

/** 모델별 공식 토큰 단가(백만 토큰당 USD)를 적용합니다. */
@Component
public class ModelPricing {
    public record Rate(double input, double cachedInput, double output) { }

    public Rate forModel(String model) {
        String normalized = model == null ? "" : model.toLowerCase();
        if (normalized.contains("luna")) return new Rate(1.00, 0.10, 6.00);
        if (normalized.contains("sol")) return new Rate(5.00, 0.50, 30.00);
        return new Rate(2.50, 0.25, 15.00); // Terra 및 알 수 없는 모델의 보수적 기본값
    }

    public double calculate(String model, long inputTokens, long cachedInputTokens, long outputTokens) {
        Rate rate = forModel(model);
        long uncachedInput = Math.max(0, inputTokens - cachedInputTokens);
        return (uncachedInput * rate.input() + cachedInputTokens * rate.cachedInput() + outputTokens * rate.output()) / 1_000_000.0;
    }
}
