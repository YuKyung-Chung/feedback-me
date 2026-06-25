package com.jyk.feedbackme.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
public class FeedbackHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String jobDescription;

    @Column(columnDefinition = "TEXT")
    private String coverLetter;

    @Column(columnDefinition = "LONGTEXT")
    private String attachmentText;

    @Column(columnDefinition = "LONGTEXT")
    private String base64Images;

    @Column(columnDefinition = "TEXT")
    private String feedbackResult;

    @Enumerated(EnumType.STRING)
    private FeedbackStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder
    public FeedbackHistory(String jobDescription, String coverLetter, String attachmentText, String base64Images, FeedbackStatus status) {
        this.jobDescription = jobDescription;
        this.coverLetter = coverLetter;
        this.attachmentText = attachmentText;
        this.base64Images = base64Images;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void startProcessing() {
        this.status = FeedbackStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    public void completeFeedback(String feedbackResult) {
        this.feedbackResult = feedbackResult;
        this.status = FeedbackStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void failFeedback() {
        this.status = FeedbackStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }
}
