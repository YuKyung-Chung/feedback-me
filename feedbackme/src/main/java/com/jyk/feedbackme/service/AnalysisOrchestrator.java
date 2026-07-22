package com.jyk.feedbackme.service;

import com.jyk.feedbackme.analysis.AnalysisStep;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Consumer;

/** 여러 분석 단계를 정해진 순서로 연결하는 하네스 실행 조정자입니다. */
@Service
/**
 * FeedbackMe 백엔드의 AnalysisOrchestrator 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.service 계층의 책임을 담당합니다.
 */
public class AnalysisOrchestrator {
    private final PromptLoader promptLoader;
    private final OpenAiClient openAiClient;
    @Value("${feedback.analysis.prompt-version:v1.0.0}") private String promptVersion;

    public AnalysisOrchestrator(PromptLoader promptLoader, OpenAiClient openAiClient) {
        this.promptLoader = promptLoader;
        this.openAiClient = openAiClient;
    }

    /** 공고·지원자·매칭·갭·보고서 단계를 순서대로 실행합니다. */
    public String analyze(String jobPosting, String candidateMaterial) throws Exception {
        return analyze(jobPosting, candidateMaterial, step -> { });
    }

    /** 각 단계 시작 시 콜백을 호출해 작업 상태 저장소와 연동합니다. */
    public String analyze(String jobPosting, String candidateMaterial, Consumer<AnalysisStep> onStepStarted) throws Exception {
        onStepStarted.accept(AnalysisStep.JOB_ANALYSIS);
        String jobAnalysis = run(AnalysisStep.JOB_ANALYSIS, "job-analysis", Map.of("jobPosting", safe(jobPosting)));
        onStepStarted.accept(AnalysisStep.CANDIDATE_ANALYSIS);
        String candidateAnalysis = run(AnalysisStep.CANDIDATE_ANALYSIS, "candidate-analysis", Map.of("candidateMaterial", safe(candidateMaterial)));
        onStepStarted.accept(AnalysisStep.MATCHING);
        String matching = run(AnalysisStep.MATCHING, "matching", Map.of(
                "jobAnalysis", jobAnalysis,
                "candidateAnalysis", candidateAnalysis));
        onStepStarted.accept(AnalysisStep.GAP_ANALYSIS);
        String gaps = run(AnalysisStep.GAP_ANALYSIS, "gap-analysis", Map.of("matching", matching));
        onStepStarted.accept(AnalysisStep.REPORT);
        return run(AnalysisStep.REPORT, "report", Map.of(
                "jobAnalysis", jobAnalysis,
                "candidateAnalysis", candidateAnalysis,
                "matching", matching,
                "gaps", gaps));
    }

    /** 프롬프트를 렌더링하고 해당 단계 모델을 호출하며 빈 응답을 차단합니다. */
    private String run(AnalysisStep step, String promptName, Map<String, String> variables) throws Exception {
        String prompt = promptLoader.load(promptName, promptVersion, variables);
        String output = openAiClient.analyzeStep(step, prompt);
        if (output == null || output.isBlank()) {
            throw new IllegalStateException("Analysis step returned an empty result: " + step);
        }
        return output;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "No material was provided." : value;
    }
}
