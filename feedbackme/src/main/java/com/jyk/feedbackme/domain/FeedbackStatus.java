package com.jyk.feedbackme.domain;

/** 분석 작업의 전체 진행 상태입니다. */
public enum FeedbackStatus {
    PENDING,
    EXTRACTING,
    JOB_ANALYZING,
    CANDIDATE_ANALYZING,
    MATCHING,
    GAP_ANALYZING,
    REPORTING,
    VERIFYING,
    PROCESSING,
    COMPLETED,
    FAILED
}
