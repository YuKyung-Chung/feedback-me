package com.jyk.feedbackme.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeedbackRequest {
    private String url;
    private String jobDescription;
}
