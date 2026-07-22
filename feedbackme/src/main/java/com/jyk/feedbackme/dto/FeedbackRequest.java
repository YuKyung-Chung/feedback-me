package com.jyk.feedbackme.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * FeedbackMe 백엔드의 FeedbackRequest 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.dto 계층의 책임을 담당합니다.
 */
public class FeedbackRequest {
    private String url;
    private String jobDescription;
}
