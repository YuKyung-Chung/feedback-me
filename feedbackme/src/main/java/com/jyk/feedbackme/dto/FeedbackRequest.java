package com.jyk.feedbackme.dto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
// 사용자 입력값 수신
public class FeedbackRequest {
    private String jobDescription;
    private String coverLetter;
}