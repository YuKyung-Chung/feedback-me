package com.jyk.feedbackme.service;

import com.jyk.feedbackme.domain.FeedbackHistory;
import com.jyk.feedbackme.domain.FeedbackStatus;
import com.jyk.feedbackme.analysis.AnalysisStep;
import com.jyk.feedbackme.repository.FeedbackHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.List;

@Component
@Profile("!test")
/**
 * FeedbackMe 백엔드의 FeedbackWorker 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.service 계층의 책임을 담당합니다.
 */
public class FeedbackWorker {

    private static final Logger log = LoggerFactory.getLogger(FeedbackWorker.class);
    private static final String QUEUE_KEY = "feedback:queue";

    private final StringRedisTemplate redisTemplate;
    private final FeedbackHistoryRepository feedbackHistoryRepository;
    private final FeedbackJobService feedbackJobService;
    private final OpenAiClient openAiClient;
    private final AnalysisOrchestrator analysisOrchestrator;
    private final TransactionTemplate transactionTemplate;
    private final CreditService creditService;

    public FeedbackWorker(StringRedisTemplate redisTemplate,
                          FeedbackHistoryRepository feedbackHistoryRepository,
                          FeedbackJobService feedbackJobService,
                          OpenAiClient openAiClient,
                          AnalysisOrchestrator analysisOrchestrator,
                          TransactionTemplate transactionTemplate,
                          CreditService creditService) {
        this.redisTemplate = redisTemplate;
        this.feedbackHistoryRepository = feedbackHistoryRepository;
        this.feedbackJobService = feedbackJobService;
        this.openAiClient = openAiClient;
        this.analysisOrchestrator = analysisOrchestrator;
        this.transactionTemplate = transactionTemplate;
        this.creditService = creditService;
    }

    @Scheduled(fixedDelayString = "${feedback.queue.poll-delay-ms:1000}")
    public void processQueue() {
        String historyIdStr = redisTemplate.opsForList().leftPop(QUEUE_KEY);
        if (historyIdStr == null) {
            return;
        }

        Long historyId = Long.parseLong(historyIdStr);
        log.info("Start feedback job. historyId={}", historyId);

        try {
            executeFeedbackProcessing(historyId);
            log.info("Completed feedback job. historyId={}", historyId);
        } catch (Exception e) {
            log.error("Failed feedback job. historyId={}", historyId, e);
            updateToFailed(historyId, e);
        }
    }

    private void executeFeedbackProcessing(Long historyId) throws Exception {
        FeedbackHistory history = markProcessing(historyId);
        if (history == null) {
            return;
        }

        String cachedResult = feedbackJobService.cachedResult(history);
        if (cachedResult != null) {
            complete(historyId, cachedResult);
            return;
        }

        String resultText;
        if (history.getBase64Images() != null && !history.getBase64Images().isBlank()) {
            List<String> base64Images = Arrays.asList(history.getBase64Images().split(","));
            resultText = openAiClient.analyzeWithVision(history.getJobDescription(), base64Images);
        } else {
            resultText = analysisOrchestrator.analyze(
                    history.getJobDescription(),
                    history.getAttachmentText(),
                    step -> updateStep(historyId, step));
        }

        complete(historyId, resultText);
        feedbackJobService.cacheResult(history, resultText);
    }

    private FeedbackHistory markProcessing(Long historyId) {
        return transactionTemplate.execute(status -> feedbackHistoryRepository.findById(historyId)
                .map(history -> {
                    history.startProcessing();
                    return feedbackHistoryRepository.save(history);
                })
                .orElse(null));
    }

    private void complete(Long historyId, String resultText) {
        transactionTemplate.executeWithoutResult(status -> feedbackHistoryRepository.findById(historyId)
                .ifPresent(history -> {
                    history.completeFeedback(resultText);
                    feedbackHistoryRepository.save(history);
                }));
    }

    private void updateStep(Long historyId, AnalysisStep step) {
        FeedbackStatus status = switch (step) {
            case JOB_ANALYSIS -> FeedbackStatus.JOB_ANALYZING;
            case CANDIDATE_ANALYSIS -> FeedbackStatus.CANDIDATE_ANALYZING;
            case MATCHING -> FeedbackStatus.MATCHING;
            case GAP_ANALYSIS -> FeedbackStatus.GAP_ANALYZING;
            case REPORT -> FeedbackStatus.REPORTING;
            case VERIFICATION -> FeedbackStatus.VERIFYING;
        };
        transactionTemplate.executeWithoutResult(tx -> feedbackHistoryRepository.findById(historyId)
                .ifPresent(history -> {
                    history.moveTo(status, step);
                    feedbackHistoryRepository.save(history);
                }));
    }

    private void updateToFailed(Long historyId, Exception error) {
        transactionTemplate.executeWithoutResult(status -> feedbackHistoryRepository.findById(historyId)
                .ifPresent(history -> {
                    history.recordRetry(error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());
                    history.failFeedback(error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());
                    feedbackHistoryRepository.save(history);
                    if (history.getUser() != null) {
                        creditService.refundForFailedAnalysis(history.getUser(), historyId);
                    }
                }));
    }
}
