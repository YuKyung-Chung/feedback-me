package com.jyk.feedbackme.controller;

import com.jyk.feedbackme.dto.FeedbackRequest;
import com.jyk.feedbackme.service.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final GeminiService geminiService;

    public FeedbackController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/feedback")
    public ResponseEntity<String> createFeedback(@RequestBody FeedbackRequest request) {
        try {
            String result = geminiService.getFeedBack(request.getJobDescription(), request.getCoverLetter());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("피드백 생성 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}