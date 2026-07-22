package com.jyk.feedbackme.service;

import com.jyk.feedbackme.analysis.AnalysisStep;
import com.jyk.feedbackme.analysis.DocumentChunk;
import com.jyk.feedbackme.analysis.EvidenceValidationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.List;
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
    private final DocumentChunker documentChunker;
    private final EvidenceValidator evidenceValidator;
    @Value("${feedback.analysis.prompt-version:v1.0.0}") private String promptVersion;

    public AnalysisOrchestrator(PromptLoader promptLoader, OpenAiClient openAiClient, DocumentChunker documentChunker, EvidenceValidator evidenceValidator) {
        this.promptLoader = promptLoader;
        this.openAiClient = openAiClient;
        this.documentChunker = documentChunker;
        this.evidenceValidator = evidenceValidator;
    }

    /** 공고·지원자·매칭·갭·보고서 단계를 순서대로 실행합니다. */
    public String analyze(String jobPosting, String candidateMaterial) throws Exception {
        return analyze(jobPosting, candidateMaterial, step -> { });
    }

    /** 각 단계 시작 시 콜백을 호출해 작업 상태 저장소와 연동합니다. */
    public String analyze(String jobPosting, String candidateMaterial, Consumer<AnalysisStep> onStepStarted) throws Exception {
        List<DocumentChunk> chunks = new java.util.ArrayList<>();
        chunks.addAll(documentChunker.chunk("job", jobPosting));
        chunks.addAll(documentChunker.chunk("candidate", candidateMaterial));
        String jobEvidence = documentChunker.formatForPrompt(documentChunker.chunk("job", jobPosting));
        String candidateEvidence = documentChunker.formatForPrompt(documentChunker.chunk("candidate", candidateMaterial));
        onStepStarted.accept(AnalysisStep.JOB_ANALYSIS);
        String jobAnalysis = run(AnalysisStep.JOB_ANALYSIS, "job-analysis", Map.of("jobPosting", jobEvidence));
        validateEvidence(AnalysisStep.JOB_ANALYSIS, jobAnalysis, chunks);
        onStepStarted.accept(AnalysisStep.CANDIDATE_ANALYSIS);
        String candidateAnalysis = run(AnalysisStep.CANDIDATE_ANALYSIS, "candidate-analysis", Map.of("candidateMaterial", candidateEvidence));
        validateEvidence(AnalysisStep.CANDIDATE_ANALYSIS, candidateAnalysis, chunks);
        onStepStarted.accept(AnalysisStep.MATCHING);
        String matching = run(AnalysisStep.MATCHING, "matching", Map.of(
                "jobAnalysis", jobAnalysis,
                "candidateAnalysis", candidateAnalysis));
        validateEvidence(AnalysisStep.MATCHING, matching, chunks);
        onStepStarted.accept(AnalysisStep.GAP_ANALYSIS);
        String gaps = run(AnalysisStep.GAP_ANALYSIS, "gap-analysis", Map.of("matching", matching));
        onStepStarted.accept(AnalysisStep.REPORT);
        return run(AnalysisStep.REPORT, "report", Map.of(
                "jobAnalysis", jobAnalysis,
                "candidateAnalysis", candidateAnalysis,
                "matching", matching,
                "gaps", gaps));
    }

    private void validateEvidence(AnalysisStep step, String output, List<DocumentChunk> chunks) {
        EvidenceValidationResult result = evidenceValidator.validate(output, chunks);
        if (!result.isValid()) {
            throw new IllegalStateException("Invalid evidence chunk IDs at " + step + ": " + result.invalidChunkIds());
        }
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
