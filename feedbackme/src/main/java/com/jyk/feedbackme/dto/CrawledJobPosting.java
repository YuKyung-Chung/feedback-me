package com.jyk.feedbackme.dto;

public record CrawledJobPosting(
        String url,
        String companyName,
        String title,
        String content
) {
}
