package com.jyk.feedbackme.analysis;

/** OpenAI Responses API가 반환한 실제 usage 정보입니다. */
public record OpenAiUsage(long inputTokens, long outputTokens, String model, double estimatedCostUsd) { }
