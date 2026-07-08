package com.jyk.feedbackme.controller;

import com.jyk.feedbackme.domain.FeedbackHistory;
import com.jyk.feedbackme.repository.FeedbackHistoryRepository;
import com.jyk.feedbackme.service.CrawlingService;
import com.jyk.feedbackme.service.FileExtractService;
import com.jyk.feedbackme.service.GeminiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final GeminiService geminiService;
    private final CrawlingService crawlingService;
    private final FileExtractService fileExtractService;
    private final FeedbackHistoryRepository feedbackHistoryRepository;

    public FeedbackController(GeminiService geminiService,
                              CrawlingService crawlingService,
                              FileExtractService fileExtractService,
                              FeedbackHistoryRepository feedbackHistoryRepository) {
        this.geminiService = geminiService;
        this.crawlingService = crawlingService;
        this.fileExtractService = fileExtractService;
        this.feedbackHistoryRepository = feedbackHistoryRepository;
    }

    @PostMapping(value = "/feedback", consumes = "multipart/form-data")
    public ResponseEntity<?> createFeedback(
            @RequestParam("url") String url,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            if (url == null || url.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Job posting URL is required."
                ));
            }

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Resume or portfolio file is required."
                ));
            }

            String jobDescription = null;
            jobDescription = crawlingService.crawl(url);

            String attachmentText = null;
            List<String> base64Images = null;
            String filename = file.getOriginalFilename();
            String lowerFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
            if (lowerFilename.endsWith(".pdf")) {
                base64Images = fileExtractService.pdfToBase64Images(file);
            } else {
                attachmentText = fileExtractService.extract(file);
            }

            Long historyId = geminiService.enqueueFeedbackRequest(jobDescription, attachmentText, base64Images);

            return ResponseEntity.accepted().body(Map.of(
                    "message", "Feedback request accepted.",
                    "historyId", historyId
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "message", "Failed to accept feedback request.",
                    "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
            ));
        }
    }

    @GetMapping("/feedback/status/{id}")
    public ResponseEntity<?> checkStatus(@PathVariable Long id) {
        FeedbackHistory history = feedbackHistoryRepository.findById(id).orElse(null);
        if (history == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", history.getStatus().name());
        response.put("result", history.getFeedbackResult() != null ? history.getFeedbackResult() : "");
        response.put("updatedAt", history.getUpdatedAt().toString());

        Integer queuePosition = geminiService.getQueuePosition(id);
        if (queuePosition != null) {
            response.put("queuePosition", queuePosition);
        }

        return ResponseEntity.ok(response);
    }
}
