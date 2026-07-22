package com.jyk.feedbackme.domain;

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

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
