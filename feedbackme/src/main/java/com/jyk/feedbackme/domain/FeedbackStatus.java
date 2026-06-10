package com.jyk.feedbackme.domain;

// 요청의 진행 상태를 나타낼 Enum 클래스
public enum FeedbackStatus {
    PENDING,     // 큐에 대기 중
    PROCESSING,  // AI 분석 진행 중
    COMPLETED,   // 피드백 완료
    FAILED       // 실패
}
