package com.jyk.feedbackme.controller;

import com.jyk.feedbackme.domain.FeedbackHistory;
import com.jyk.feedbackme.repository.FeedbackHistoryRepository;
import com.jyk.feedbackme.service.CrawlingService;
import com.jyk.feedbackme.service.FileExtractService;
import com.jyk.feedbackme.service.GeminiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final GeminiService geminiService;
    private final CrawlingService crawlingService;
    private final FileExtractService fileExtractService;
    private final FeedbackHistoryRepository feedbackHistoryRepository; // 상태 조회를 위해 주입

    public FeedbackController(GeminiService geminiService,
                              CrawlingService crawlingService,
                              FileExtractService fileExtractService,
                              FeedbackHistoryRepository feedbackHistoryRepository) {
        this.geminiService = geminiService;
        this.crawlingService = crawlingService;
        this.fileExtractService = fileExtractService;
        this.feedbackHistoryRepository = feedbackHistoryRepository;
    }

    // 1. 피드백 요청 접수 (비동기 큐잉)
    @PostMapping(value = "/feedback", consumes = "multipart/form-data")
    public ResponseEntity<?> createFeedback(
            @RequestParam("url") String url,
            @RequestParam("coverLetter") String coverLetter,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            // 무거운 외부 API 호출 전에 전처리 작업(크롤링, 파일 추출)은 컨트롤러 단에서 빠르게 수행
            String jobDescription = null;
            if (url != null && !url.isBlank()) {
                jobDescription = crawlingService.crawl(url);
            }

            List<String> base64Images = null;
            if (file != null && !file.isEmpty()) {
                base64Images = fileExtractService.pdfToBase64Images(file);
            }

            // 모든 재료를 수집하여 Redis 큐에 적재 (이 단계는 아주 빠르게 끝납니다)
            Long historyId = geminiService.enqueueFeedbackRequest(jobDescription, coverLetter, null, base64Images);

            // 사용자가 화면에서 상태를 추적할 수 있도록 생성된 ID를 JSON으로 반환
            return ResponseEntity.ok(Map.of(
                    "message", "피드백 요청이 성공적으로 접수되었습니다.",
                    "historyId", historyId
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("오류가 발생했습니다: " + e.getMessage());
        }
    }

    // 2. 프론트엔드 자바스크립트가 주기적으로 상태를 확인할 Polling API
    @GetMapping("/feedback/status/{id}")
    public ResponseEntity<?> checkStatus(@PathVariable Long id) {
        FeedbackHistory history = feedbackHistoryRepository.findById(id).orElse(null);
        if (history == null) {
            return ResponseEntity.notFound().build();
        }

        // 현재 상태(PENDING, COMPLETED, FAILED 등)와 최종 결과물을 묶어서 반환
        return ResponseEntity.ok(Map.of(
                "status", history.getStatus().name(),
                "result", history.getFeedbackResult() != null ? history.getFeedbackResult() : ""
        ));
    }
}