package com.jyk.feedbackme.service;

import com.jyk.feedbackme.domain.FeedbackHistory;
import com.jyk.feedbackme.repository.FeedbackHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.List;

@Component
@Profile("!test")
public class FeedbackWorker {

    private static final Logger log = LoggerFactory.getLogger(FeedbackWorker.class);
    private static final String QUEUE_KEY = "feedback:queue";

    private final RedisTemplate<String, String> redisTemplate;
    private final FeedbackHistoryRepository feedbackHistoryRepository;
    private final GeminiService geminiService;
    private final TransactionTemplate transactionTemplate;

    public FeedbackWorker(RedisTemplate<String, String> redisTemplate,
                          FeedbackHistoryRepository feedbackHistoryRepository,
                          GeminiService geminiService,
                          TransactionTemplate transactionTemplate) {
        this.redisTemplate = redisTemplate;
        this.feedbackHistoryRepository = feedbackHistoryRepository;
        this.geminiService = geminiService;
        this.transactionTemplate = transactionTemplate;
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
            updateToFailed(historyId);
        }
    }

    private void executeFeedbackProcessing(Long historyId) throws Exception {
        FeedbackHistory history = markProcessing(historyId);
        if (history == null) {
            return;
        }

        String cachedResult = geminiService.getCachedResult(history);
        if (cachedResult != null) {
            complete(historyId, cachedResult);
            return;
        }

        String resultText;
        if (history.getBase64Images() != null && !history.getBase64Images().isBlank()) {
            List<String> base64Images = Arrays.asList(history.getBase64Images().split(","));
            resultText = geminiService.getFeedBackWithVision(history.getJobDescription(), history.getCoverLetter(), base64Images);
        } else {
            resultText = geminiService.getFeedBack(history.getJobDescription(), history.getCoverLetter(), history.getAttachmentText());
        }

        complete(historyId, resultText);
        geminiService.cacheResult(history, resultText);
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

    private void updateToFailed(Long historyId) {
        transactionTemplate.executeWithoutResult(status -> feedbackHistoryRepository.findById(historyId)
                .ifPresent(history -> {
                    history.failFeedback();
                    feedbackHistoryRepository.save(history);
                }));
    }
}
