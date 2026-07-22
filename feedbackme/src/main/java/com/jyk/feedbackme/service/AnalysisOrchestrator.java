package com.jyk.feedbackme.service;

import com.jyk.feedbackme.analysis.AnalysisStep;
import com.jyk.feedbackme.analysis.DocumentChunk;
import com.jyk.feedbackme.analysis.EvidenceValidationResult;
import com.jyk.feedbackme.analysis.StepSchemaValidationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

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
    private final StepSchemaValidator stepSchemaValidator;
    private final AnalysisRetryPolicy retryPolicy;
    @Value("${feedback.analysis.prompt-version:v1.0.0}") private String promptVersion;

    public AnalysisOrchestrator(PromptLoader promptLoader, OpenAiClient openAiClient, DocumentChunker documentChunker, EvidenceValidator evidenceValidator, StepSchemaValidator stepSchemaValidator, AnalysisRetryPolicy retryPolicy) {
        this.promptLoader = promptLoader;
        this.openAiClient = openAiClient;
        this.documentChunker = documentChunker;
        this.evidenceValidator = evidenceValidator;
        this.stepSchemaValidator = stepSchemaValidator;
        this.retryPolicy = retryPolicy;
    }

    /** 공고·지원자·매칭·갭·보고서 단계를 순서대로 실행합니다. */
    public String analyze(String jobPosting, String candidateMaterial) throws Exception {
        return analyze(jobPosting, candidateMaterial, (step, output) -> { });
    }

    /** 각 단계 시작 시 콜백을 호출해 작업 상태 저장소와 연동합니다. */
    public String analyze(String jobPosting, String candidateMaterial, Consumer<AnalysisStep> onStepStarted) throws Exception {
        return analyze(jobPosting, candidateMaterial, (step, output) -> onStepStarted.accept(step));
    }

    /** 단계 시작과 결과를 모두 전달해 작업 저장소가 체크포인트를 기록하도록 합니다. */
    public String analyze(String jobPosting, String candidateMaterial, BiConsumer<AnalysisStep, String> onStepCompleted) throws Exception {
        return analyze(jobPosting, candidateMaterial, onStepCompleted, new java.util.EnumMap<>(AnalysisStep.class));
    }

    public String analyze(String jobPosting, String candidateMaterial, BiConsumer<AnalysisStep, String> onStepCompleted, Map<AnalysisStep, String> checkpoint) throws Exception {
        List<DocumentChunk> chunks = new java.util.ArrayList<>();
        chunks.addAll(documentChunker.chunk("job", jobPosting));
        chunks.addAll(documentChunker.chunk("candidate", candidateMaterial));
        String jobEvidence = documentChunker.formatForPrompt(documentChunker.chunk("job", jobPosting));
        String candidateEvidence = documentChunker.formatForPrompt(documentChunker.chunk("candidate", candidateMaterial));
        onStepCompleted.accept(AnalysisStep.JOB_ANALYSIS, null);
        String jobAnalysis = checkpoint.containsKey(AnalysisStep.JOB_ANALYSIS) ? checkpoint.get(AnalysisStep.JOB_ANALYSIS) : run(AnalysisStep.JOB_ANALYSIS, "job-analysis", Map.of("jobPosting", jobEvidence));
        onStepCompleted.accept(AnalysisStep.JOB_ANALYSIS, jobAnalysis);
        validateEvidence(AnalysisStep.JOB_ANALYSIS, jobAnalysis, chunks);
        onStepCompleted.accept(AnalysisStep.CANDIDATE_ANALYSIS, null);
        String candidateAnalysis = checkpoint.containsKey(AnalysisStep.CANDIDATE_ANALYSIS) ? checkpoint.get(AnalysisStep.CANDIDATE_ANALYSIS) : run(AnalysisStep.CANDIDATE_ANALYSIS, "candidate-analysis", Map.of("candidateMaterial", candidateEvidence));
        onStepCompleted.accept(AnalysisStep.CANDIDATE_ANALYSIS, candidateAnalysis);
        validateEvidence(AnalysisStep.CANDIDATE_ANALYSIS, candidateAnalysis, chunks);
        onStepCompleted.accept(AnalysisStep.MATCHING, null);
        String matching = checkpoint.containsKey(AnalysisStep.MATCHING) ? checkpoint.get(AnalysisStep.MATCHING) : run(AnalysisStep.MATCHING, "matching", Map.of(
                "jobAnalysis", jobAnalysis,
                "candidateAnalysis", candidateAnalysis));
        onStepCompleted.accept(AnalysisStep.MATCHING, matching);
        validateEvidence(AnalysisStep.MATCHING, matching, chunks);
        onStepCompleted.accept(AnalysisStep.GAP_ANALYSIS, null);
        String gaps = checkpoint.containsKey(AnalysisStep.GAP_ANALYSIS) ? checkpoint.get(AnalysisStep.GAP_ANALYSIS) : run(AnalysisStep.GAP_ANALYSIS, "gap-analysis", Map.of("matching", matching));
        onStepCompleted.accept(AnalysisStep.GAP_ANALYSIS, gaps);
        onStepCompleted.accept(AnalysisStep.REPORT, null);
        String report = checkpoint.containsKey(AnalysisStep.REPORT) ? checkpoint.get(AnalysisStep.REPORT) : run(AnalysisStep.REPORT, "report", Map.of(
                "jobAnalysis", jobAnalysis,
                "candidateAnalysis", candidateAnalysis,
                "matching", matching,
                "gaps", gaps));
        onStepCompleted.accept(AnalysisStep.REPORT, report);
        return report;
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
        Exception lastError = null;
        for (int attempt = 1; attempt <= AnalysisRetryPolicy.MAX_ATTEMPTS; attempt++) {
            try {
                String output = openAiClient.analyzeStep(step, prompt);
                if (output == null || output.isBlank()) throw new IllegalStateException("Analysis step returned an empty result: " + step);
                StepSchemaValidationResult schema = stepSchemaValidator.validate(step, output);
                if (!schema.valid()) throw new IllegalStateException("Invalid schema at " + step + ": " + schema.errors());
                return output;
            } catch (Exception error) {
                lastError = error;
                if (!retryPolicy.isRetryable(error) || attempt == AnalysisRetryPolicy.MAX_ATTEMPTS) throw error;
                Thread.sleep(retryPolicy.backoffMillis(attempt));
            }
        }
        throw lastError;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "No material was provided." : value;
    }
}
