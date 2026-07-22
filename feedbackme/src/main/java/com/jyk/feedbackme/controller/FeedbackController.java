package com.jyk.feedbackme.controller;

import com.jyk.feedbackme.domain.AppUser;
import com.jyk.feedbackme.domain.FeedbackHistory;
import com.jyk.feedbackme.domain.FeedbackStatus;
import com.jyk.feedbackme.dto.CrawledJobPosting;
import com.jyk.feedbackme.repository.FeedbackHistoryRepository;
import com.jyk.feedbackme.service.CrawlingService;
import com.jyk.feedbackme.service.FileExtractService;
import com.jyk.feedbackme.service.FeedbackJobService;
import com.jyk.feedbackme.service.AuthService;
import com.jyk.feedbackme.service.CreditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
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
/**
 * FeedbackMe 백엔드의 FeedbackController 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.controller 계층의 책임을 담당합니다.
 */
public class FeedbackController {

    private final FeedbackJobService feedbackJobService;
    private final CrawlingService crawlingService;
    private final FileExtractService fileExtractService;
    private final FeedbackHistoryRepository feedbackHistoryRepository;
    private final AuthService authService;
    private final CreditService creditService;

    public FeedbackController(FeedbackJobService feedbackJobService,
                              CrawlingService crawlingService,
                              FileExtractService fileExtractService,
                              FeedbackHistoryRepository feedbackHistoryRepository,
                              AuthService authService,
                              CreditService creditService) {
        this.feedbackJobService = feedbackJobService;
        this.crawlingService = crawlingService;
        this.fileExtractService = fileExtractService;
        this.feedbackHistoryRepository = feedbackHistoryRepository;
        this.authService = authService;
        this.creditService = creditService;
    }

    @PostMapping(value = "/feedback", consumes = "multipart/form-data")
    public ResponseEntity<?> createFeedback(
            HttpServletRequest request,
            @RequestParam("url") String url,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            AppUser user = authService.getCurrentUser(request).orElse(null);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "message", "로그인이 필요합니다."
                ));
            }
            creditService.grantSignupCreditsIfMissing(user);

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

            if (!creditService.hasAnalysisCredit(user)) {
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of(
                        "message", "분석권이 부족합니다. 분석권을 충전해 주세요."
                ));
            }

            String trimmedUrl = url.trim();
            CrawledJobPosting posting = crawlingService.crawlPosting(trimmedUrl);
            String jobDescription = posting.content();

            String attachmentText = null;
            List<String> base64Images = null;
            String filename = file.getOriginalFilename();
            String lowerFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
            if (lowerFilename.endsWith(".pdf")) {
                base64Images = fileExtractService.pdfToBase64Images(file);
            } else {
                attachmentText = fileExtractService.extract(file);
            }

            Long historyId = feedbackJobService.enqueue(
                    user,
                    trimmedUrl,
                    posting.companyName(),
                    posting.title(),
                    filename,
                    jobDescription,
                    attachmentText,
                    base64Images
            );

            return ResponseEntity.accepted().body(Map.of(
                    "message", "Feedback request accepted.",
                    "historyId", historyId
            ));
        } catch (Exception e) {
            if ("분석권이 부족합니다.".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of(
                        "message", "분석권이 부족합니다. 분석권을 충전해 주세요."
                ));
            }
            return ResponseEntity.internalServerError().body(Map.of(
                    "message", "Failed to accept feedback request.",
                    "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
            ));
        }
    }

    @GetMapping("/feedback/status/{id}")
    public ResponseEntity<?> checkStatus(HttpServletRequest request, @PathVariable Long id) {
        AppUser user = authService.getCurrentUser(request).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "message", "로그인이 필요합니다."
            ));
        }

        FeedbackHistory history = feedbackHistoryRepository.findByIdAndUser(id, user).orElse(null);
        if (history == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", history.getStatus().name());
        response.put("currentStep", history.getCurrentStep() != null ? history.getCurrentStep().name() : "");
        response.put("retryCount", history.getRetryCount());
        response.put("lastError", history.getLastError() != null ? history.getLastError() : "");
        response.put("evidenceValidationStatus", history.getEvidenceValidationStatus() != null ? history.getEvidenceValidationStatus() : "");
        response.put("checkpointAvailable", history.getStepResultsJson() != null && !history.getStepResultsJson().isBlank());
        response.put("estimatedOutputTokens", history.getEstimatedOutputTokens());
        response.put("estimatedCostUsd", history.getEstimatedCostUsd());
        response.put("result", history.getFeedbackResult() != null ? history.getFeedbackResult() : "");
        response.put("updatedAt", history.getUpdatedAt().toString());

        Integer queuePosition = feedbackJobService.queuePosition(id);
        if (queuePosition != null) {
            response.put("queuePosition", queuePosition);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/feedback/history")
    public ResponseEntity<?> getHistory(HttpServletRequest request) {
        AppUser user = authService.getCurrentUser(request).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "message", "로그인이 필요합니다."
            ));
        }

        List<Map<String, Object>> histories = feedbackHistoryRepository.findTop20ByUserAndStatusOrderByCreatedAtDesc(user, FeedbackStatus.COMPLETED)
                .stream()
                .map(history -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", history.getId());
                    item.put("status", history.getStatus().name());
                    item.put("jobUrl", history.getJobUrl() != null ? history.getJobUrl() : "");
                    item.put("companyName", history.getCompanyName() != null ? history.getCompanyName() : "");
                    item.put("jobTitle", history.getJobTitle() != null ? history.getJobTitle() : "");
                    item.put("attachmentName", history.getAttachmentName() != null ? history.getAttachmentName() : "");
                    item.put("hasResult", history.getFeedbackResult() != null && !history.getFeedbackResult().isBlank());
                    item.put("createdAt", history.getCreatedAt().toString());
                    item.put("updatedAt", history.getUpdatedAt().toString());
                    return item;
                })
                .toList();

        return ResponseEntity.ok(Map.of("histories", histories));
    }
}
