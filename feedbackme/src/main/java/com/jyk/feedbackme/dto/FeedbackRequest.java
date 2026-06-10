package com.jyk.feedbackme.dto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
// 사용자 입력값 수신
public class FeedbackRequest {
    private String url; // 채용공고 URL(크롤링용)
    private String jobDescription; //URL 없을 때 직접 입력(선택)
    private String coverLetter;
}