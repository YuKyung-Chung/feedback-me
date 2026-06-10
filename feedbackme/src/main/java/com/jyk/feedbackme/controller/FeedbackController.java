package com.jyk.feedbackme.controller;

import com.jyk.feedbackme.dto.FeedbackRequest;
import com.jyk.feedbackme.service.CrawlingService;
import com.jyk.feedbackme.service.GeminiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final GeminiService geminiService;
    private final CrawlingService crawlingService;

    public FeedbackController(GeminiService geminiService, CrawlingService crawlingService) {
        this.geminiService = geminiService;
        this.crawlingService = crawlingService;
    }

    @PostMapping("/feedback")
    public ResponseEntity<String> createFeedback(@RequestBody FeedbackRequest request) {
        try {
            String jobDescription = request.getJobDescription();

            // URL이 있으면 크롤링해서 jobDescription으로 사용
            if(request.getUrl() != null && !request.getUrl().isBlank()){
                jobDescription = crawlingService.crawl(request.getUrl());
            }

            String result = geminiService.getFeedBack(jobDescription, request.getCoverLetter());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("피드백 생성 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}