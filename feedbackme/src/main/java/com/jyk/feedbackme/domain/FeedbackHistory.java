package com.jyk.feedbackme.domain;

import com.jyk.feedbackme.analysis.AnalysisStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "feedback_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/**
 * FeedbackMe 백엔드의 FeedbackHistory 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.domain 계층의 책임을 담당합니다.
 */
public class FeedbackHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    private String jobUrl;

    private String companyName;

    private String jobTitle;

    private String attachmentName;

    @Column(columnDefinition = "TEXT")
    private String jobDescription;

    @Column(columnDefinition = "LONGTEXT")
    private String attachmentText;

    @Column(columnDefinition = "LONGTEXT")
    private String base64Images;

    @Column(columnDefinition = "TEXT")
    private String feedbackResult;

    @Enumerated(EnumType.STRING)
    private FeedbackStatus status;

    @Enumerated(EnumType.STRING)
    private AnalysisStep currentStep;

    private int retryCount;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @Column(columnDefinition = "LONGTEXT")
    private String documentChunksJson;

    private String evidenceValidationStatus;

    @Column(columnDefinition = "LONGTEXT")
    private String stepResultsJson;

    private long estimatedOutputTokens;
    private double estimatedCostUsd;

    @Builder
    public FeedbackHistory(AppUser user, String jobUrl, String companyName, String jobTitle, String attachmentName, String jobDescription, String attachmentText, String base64Images, FeedbackStatus status) {
        this.user = user;
        this.jobUrl = jobUrl;
        this.companyName = companyName;
        this.jobTitle = jobTitle;
        this.attachmentName = attachmentName;
        this.jobDescription = jobDescription;
        this.attachmentText = attachmentText;
        this.base64Images = base64Images;
        this.status = status;
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void startProcessing() {
        this.status = FeedbackStatus.PROCESSING;
        this.startedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void moveTo(FeedbackStatus status, AnalysisStep step) {
        this.status = status;
        this.currentStep = step;
        this.updatedAt = LocalDateTime.now();
    }

    public void recordRetry(String errorMessage) {
        this.retryCount++;
        this.lastError = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }

    public void completeFeedback(String feedbackResult) {
        this.feedbackResult = feedbackResult;
        this.status = FeedbackStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.lastError = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void failFeedback() {
        this.status = FeedbackStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    public void failFeedback(String errorMessage) {
        this.status = FeedbackStatus.FAILED;
        this.lastError = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }

    /** 재시도 가능한 실패를 대기 상태로 되돌립니다. */
    public void prepareRetry(String errorMessage) {
        this.retryCount++;
        this.lastError = errorMessage;
        this.status = FeedbackStatus.PENDING;
        this.updatedAt = LocalDateTime.now();
    }

    /** 실행 시점의 청크 목록과 근거 검증 상태를 체크포인트로 기록합니다. */
    public void recordEvidenceCheckpoint(String chunksJson, String validationStatus) {
        this.documentChunksJson = chunksJson;
        this.evidenceValidationStatus = validationStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public void markEvidenceValidation(String validationStatus) {
        this.evidenceValidationStatus = validationStatus;
        this.updatedAt = LocalDateTime.now();
    }

    /** 단계별 분석 결과를 JSON 문자열 체크포인트로 누적합니다. */
    public void recordStepResult(String step, String result) {
        String entry = "{\"step\":\"" + step + "\",\"result\":\"" + result.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        this.stepResultsJson = (this.stepResultsJson == null || this.stepResultsJson.isBlank()) ? "[" + entry + "]" : this.stepResultsJson.replaceAll("\\]$", "," + entry + "]");
        this.updatedAt = LocalDateTime.now();
    }

    /** 단계 결과의 추정 출력 토큰과 비용을 누적합니다. */
    public void recordEstimatedCost(long outputTokens, double costUsd) {
        this.estimatedOutputTokens += outputTokens;
        this.estimatedCostUsd += costUsd;
        this.updatedAt = LocalDateTime.now();
    }
}
