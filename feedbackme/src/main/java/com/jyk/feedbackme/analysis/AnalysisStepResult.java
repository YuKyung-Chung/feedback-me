package com.jyk.feedbackme.analysis;

import java.time.Duration;

/** 한 단계의 결과와 처리 메타데이터를 담는 값 객체입니다. */
/**
 * FeedbackMe 백엔드의 AnalysisStepResult 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.analysis 계층의 책임을 담당합니다.
 */
public record AnalysisStepResult(
        AnalysisStep step,
        String model,
        String promptVersion,
        String output,
        Duration duration,
        int retryCount,
        boolean validated
) {
}
