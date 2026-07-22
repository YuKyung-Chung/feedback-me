package com.jyk.feedbackme.service;

import org.springframework.stereotype.Component;

/** 단계별 재시도 가능 여부와 백오프 시간을 결정합니다. */
@Component
public class AnalysisRetryPolicy {
    public static final int MAX_ATTEMPTS = 3;

    public boolean isRetryable(Exception error) {
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        return message.contains("timeout") || message.contains("timed out")
                || message.contains("429") || message.contains("5xx")
                || message.contains("invalid schema") || message.contains("invalid evidence");
    }

    public long backoffMillis(int failedAttempt) {
        return 500L * (1L << Math.max(0, failedAttempt - 1));
    }
}
