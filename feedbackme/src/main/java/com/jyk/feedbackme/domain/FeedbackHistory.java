package com.jyk.feedbackme.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// feedback_history 테이블과 1:1로 매핑될 핵심 엔티티 클래스
@Entity
@Table(name="feedback_history")
@Getter
@NoArgsConstructor(access= AccessLevel.PROTECTED)
public class FeedbackHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String jobDescription; //채용 공고 원문

    @Column(columnDefinition = "TEXT")
    private String coverLetter; //자기소개서 원문

    @Column(columnDefinition = "TEXT")
    private String feedbackResult; // Gemini API가 반환한 결과 리포트

    @Enumerated(EnumType.STRING)
    private FeedbackStatus status; //PENDING, PROCESSING, COMPLETED, FAILED 상태

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder
    public FeedbackHistory(String jobDescription, String coverLetter, FeedbackStatus status) {
        this.jobDescription = jobDescription;
        this.coverLetter = coverLetter;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 비동기 처리 완료 후 결과를 저장하고 상태를 완료로 바꾸기 위한 메서드
    public void completeFeedback(String feedbackResult){
        this.feedbackResult = feedbackResult;
        this.status = FeedbackStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    // 실패 시 상태를 변경하기 위한 메서드
    public void failFeedback(){
        this.status = FeedbackStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

}
