package com.jyk.feedbackme.service;

import com.jyk.feedbackme.domain.FeedbackHistory;
import com.jyk.feedbackme.repository.FeedbackHistoryRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Component
@Profile("!test")
public class FeedbackWorker {

    private final RedisTemplate<String, String> redisTemplate;
    private final FeedbackHistoryRepository feedbackHistoryRepository;
    private final GeminiService geminiService;

    private static final String QUEUE_KEY = "feedback:queue";

    public FeedbackWorker(RedisTemplate<String, String> redisTemplate,
                          FeedbackHistoryRepository feedbackHistoryRepository,
                          GeminiService geminiService) {
        this.redisTemplate = redisTemplate;
        this.feedbackHistoryRepository = feedbackHistoryRepository;
        this.geminiService = geminiService;
    }

    // 1초마다 실행하되, 이전 작업이 끝나고 1초 뒤에 실행되도록 fixedDelay 설정
    @Scheduled(fixedDelay = 1000)
    public void processQueue() {
        // 1. Redis 큐에서 작업 하나 꺼내기 (메소드 전체 트랜잭션 제거)
        String historyIdStr = redisTemplate.opsForList().leftPop(QUEUE_KEY);
        if (historyIdStr == null) return;

        System.out.println("====== [Worker] 큐에서 일감 발견! 처리 시작 ID: " + historyIdStr + " ======");
        Long historyId = Long.parseLong(historyIdStr);

        try {
            // 2. 실제 AI 호출 및 DB 반영 로직 수행
            executeFeedbackProcessing(historyId);
        } catch (Exception e) {
            System.err.println("====== [Worker] 처리 중 치명적 에러 발생 ======");
            e.printStackTrace();
            // 실패 시 DB 상태 업데이트
            updateToFailed(historyId);
        }
    }

    // 실제 무거운 작업을 수행하고 결과를 저장하는 메소드 (트랜잭션 분리)
    @Transactional
    public void executeFeedbackProcessing(Long historyId) throws Exception {
        FeedbackHistory history = feedbackHistoryRepository.findById(historyId).orElse(null);
        if (history == null) return;

        String resultText;

        if (history.getBase64Images() != null && !history.getBase64Images().isBlank()) {
            List<String> base64Images = Arrays.asList(history.getBase64Images().split(","));
            resultText = geminiService.getFeedBackWithVision(history.getJobDescription(), history.getCoverLetter(), base64Images, history);
        } else {
            resultText = geminiService.getFeedBack(history.getJobDescription(), history.getCoverLetter(), history.getAttachmentText(), history);
        }

        // [중요] 기존 GeminiService 안에서 또 save()를 하고 있다면 데이터가 중복되거나 유실될 수 있으므로,
        // Worker에서 최종 조립된 결과를 확실하게 영속성 컨텍스트에 반영합니다.
        history.completeFeedback(resultText);
        feedbackHistoryRepository.save(history);
        System.out.println("====== [Worker] AI 피드백 성공적으로 완료! DB 반영 완료 ======");
    }

    @Transactional
    public void updateToFailed(Long historyId) {
        FeedbackHistory history = feedbackHistoryRepository.findById(historyId).orElse(null);
        if (history != null) {
            history.failFeedback();
            feedbackHistoryRepository.save(history);
        }
    }
}
