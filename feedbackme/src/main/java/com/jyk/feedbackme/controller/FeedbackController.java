package com.jyk.feedbackme.controller;

import com.jyk.feedbackme.service.CrawlingService;
import com.jyk.feedbackme.service.FileExtractService;
import com.jyk.feedbackme.service.GeminiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final GeminiService geminiService;
    private final CrawlingService crawlingService;
    private final FileExtractService fileExtractService;

    public FeedbackController(GeminiService geminiService, CrawlingService crawlingService, FileExtractService fileExtractService) {
        this.geminiService = geminiService;
        this.crawlingService = crawlingService;
        this.fileExtractService = fileExtractService;
    }

    @PostMapping(value = "/feedback", consumes = "multipart/form-data")
    public ResponseEntity<String> createFeedback(
            @RequestParam("url") String url,
            @RequestParam("coverLetter") String coverLetter,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            String jobDescription = null;
            if (url != null && !url.isBlank()) {
                jobDescription = crawlingService.crawl(url);
            }

            String result;
            if (file != null && !file.isEmpty()) {
                // PDF 이미지 변환 후 Vision API 호출
                List<String> base64Images = fileExtractService.pdfToBase64Images(file);
                result = geminiService.getFeedBackWithVision(jobDescription, coverLetter, base64Images);
            } else {
                // 파일 없으면 텍스트만으로 피드백
                result = geminiService.getFeedBack(jobDescription, coverLetter, null);
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("오류가 발생했습니다: " + e.getMessage());
        }
    }
}