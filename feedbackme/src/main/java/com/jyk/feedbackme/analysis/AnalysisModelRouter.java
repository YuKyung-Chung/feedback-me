package com.jyk.feedbackme.analysis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 단계별로 사용할 OpenAI 모델을 선택하는 라우터입니다. */
@Component
/**
 * FeedbackMe 백엔드의 AnalysisModelRouter 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.analysis 계층의 책임을 담당합니다.
 */
public class AnalysisModelRouter {
    @Value("${openai.models.job-analysis:gpt-5.6-luna}") private String jobAnalysis;
    @Value("${openai.models.candidate-analysis:gpt-5.6-luna}") private String candidateAnalysis;
    @Value("${openai.models.matching:gpt-5.6-terra}") private String matching;
    @Value("${openai.models.report:gpt-5.6-terra}") private String report;
    @Value("${openai.models.verification:gpt-5.6-sol}") private String verification;

    /** 설정 파일의 모델 ID를 단계에 맞게 반환합니다. */
    public String modelFor(AnalysisStep step) {
        return switch (step) {
            case JOB_ANALYSIS -> jobAnalysis;
            case CANDIDATE_ANALYSIS -> candidateAnalysis;
            case MATCHING, GAP_ANALYSIS -> matching;
            case REPORT -> report;
            case VERIFICATION -> verification;
        };
    }
}
