package com.jyk.feedbackme.analysis;

/** 하네스가 실행하는 분석 단계의 표준 목록입니다. */
/**
 * FeedbackMe 백엔드의 AnalysisStep 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.analysis 계층의 책임을 담당합니다.
 */
public enum AnalysisStep {
    JOB_ANALYSIS,
    CANDIDATE_ANALYSIS,
    MATCHING,
    GAP_ANALYSIS,
    REPORT,
    VERIFICATION
}
