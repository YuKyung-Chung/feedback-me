package com.jyk.feedbackme.service;

import com.jyk.feedbackme.domain.AppUser;
import com.jyk.feedbackme.domain.FeedbackHistory;
import com.jyk.feedbackme.domain.FeedbackStatus;
import com.jyk.feedbackme.repository.FeedbackHistoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Service
public class FeedbackJobService {
    private static final String QUEUE_KEY = "feedback:queue";
    private static final String CACHE_PREFIX = "feedback:result:";
    private final FeedbackHistoryRepository repository;
    private final StringRedisTemplate redis;
    private final CreditService creditService;
    @Value("${feedback.cache.result-ttl-seconds:604800}") private long cacheTtlSeconds;
    @Value("${feedback.analysis.cache-key-version:openai-harness-v1}") private String cacheKeyVersion;

    public FeedbackJobService(FeedbackHistoryRepository repository, StringRedisTemplate redis, CreditService creditService) {
        this.repository = repository;
        this.redis = redis;
        this.creditService = creditService;
    }

    @Transactional
    public Long enqueue(AppUser user, String jobUrl, String companyName, String jobTitle, String attachmentName,
                        String jobDescription, String attachmentText, List<String> base64Images) {
        String images = base64Images == null ? "" : String.join(",", base64Images);
        FeedbackHistory history = FeedbackHistory.builder().user(user).jobUrl(jobUrl).companyName(companyName)
                .jobTitle(jobTitle).attachmentName(attachmentName).jobDescription(jobDescription)
                .attachmentText(attachmentText).base64Images(images).status(FeedbackStatus.PENDING).build();
        String cached = redis.opsForValue().get(cacheKey(history));
        if (cached != null) {
            history.completeFeedback(cached);
            repository.save(history);
            creditService.useForAnalysis(user, history.getId());
            return history.getId();
        }
        repository.save(history);
        creditService.useForAnalysis(user, history.getId());
        redis.opsForList().rightPush(QUEUE_KEY, history.getId().toString());
        return history.getId();
    }

    public String cachedResult(FeedbackHistory history) { return redis.opsForValue().get(cacheKey(history)); }
    public void cacheResult(FeedbackHistory history, String result) { redis.opsForValue().set(cacheKey(history), result, Duration.ofSeconds(cacheTtlSeconds)); }
    public Integer queuePosition(Long id) {
        List<String> ids = redis.opsForList().range(QUEUE_KEY, 0, -1);
        return ids == null ? null : (ids.indexOf(id.toString()) < 0 ? null : ids.indexOf(id.toString()) + 1);
    }
    private String cacheKey(FeedbackHistory h) {
        String raw = cacheKeyVersion + "\n" + nullSafe(h.getJobDescription()) + "\n" + nullSafe(h.getAttachmentText()) + "\n" + nullSafe(h.getBase64Images());
        try { return CACHE_PREFIX + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Unable to create analysis cache key", e); }
    }
    private String nullSafe(String value) { return value == null ? "" : value.trim(); }
}
