package com.jyk.feedbackme.controller;

import com.jyk.feedbackme.service.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FeedbackController {

    private final GeminiService geminiService;

    @PostMapping("/feedback")
    public ResponseEntity<String> getFeedback(@RequestBody FeedbackRequest request) throws Exception{
        String result = geminiService.getFeedBack(
                request.jobDescription(),
                request.coverLetter()
        );
        return ResponseEntity.ok(result);
    }

    public record FeedbackRequest(String jobDescription, String coverLetter) {}
}
