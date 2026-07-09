package com.jyk.feedbackme.service;

import com.jyk.feedbackme.domain.AppUser;
import com.jyk.feedbackme.domain.FeedbackHistory;
import com.jyk.feedbackme.domain.FeedbackStatus;
import com.jyk.feedbackme.repository.FeedbackHistoryRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Service
public class GeminiService {

    private static final String QUEUE_KEY = "feedback:queue";
    private static final String CACHE_PREFIX = "feedback:result:";
    private static final int MAX_GEMINI_ATTEMPTS = 3;

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${feedback.cache.result-ttl-seconds:604800}")
    private long resultCacheTtlSeconds;

    private final FeedbackHistoryRepository feedbackHistoryRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final CreditService creditService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public GeminiService(FeedbackHistoryRepository feedbackHistoryRepository, RedisTemplate<String, String> redisTemplate, CreditService creditService) {
        this.feedbackHistoryRepository = feedbackHistoryRepository;
        this.redisTemplate = redisTemplate;
        this.creditService = creditService;
    }

    @Transactional
    public Long enqueueFeedbackRequest(AppUser user, String jobUrl, String companyName, String jobTitle, String attachmentName, String jobDescription, String attachmentText, List<String> base64Images) {
        String imagesCsv = toCsv(base64Images);
        String cacheKey = createCacheKey(jobDescription, attachmentText, imagesCsv);
        String cachedResult = redisTemplate.opsForValue().get(cacheKey);

        FeedbackHistory history = FeedbackHistory.builder()
                .user(user)
                .jobUrl(jobUrl)
                .companyName(companyName)
                .jobTitle(jobTitle)
                .attachmentName(attachmentName)
                .jobDescription(jobDescription)
                .attachmentText(attachmentText)
                .base64Images(imagesCsv)
                .status(FeedbackStatus.PENDING)
                .build();

        if (cachedResult != null) {
            history.completeFeedback(cachedResult);
            feedbackHistoryRepository.save(history);
            creditService.useForAnalysis(user, history.getId());
            return history.getId();
        }

        feedbackHistoryRepository.save(history);
        creditService.useForAnalysis(user, history.getId());
        redisTemplate.opsForList().rightPush(QUEUE_KEY, history.getId().toString());
        return history.getId();
    }

    public String getCachedResult(FeedbackHistory history) {
        return redisTemplate.opsForValue().get(createCacheKey(history));
    }

    public void cacheResult(FeedbackHistory history, String result) {
        redisTemplate.opsForValue().set(createCacheKey(history), result, Duration.ofSeconds(resultCacheTtlSeconds));
    }

    public Integer getQueuePosition(Long historyId) {
        List<String> queuedIds = redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
        if (queuedIds == null || queuedIds.isEmpty()) {
            return null;
        }

        String targetId = historyId.toString();
        for (int index = 0; index < queuedIds.size(); index++) {
            if (targetId.equals(queuedIds.get(index))) {
                return index + 1;
            }
        }

        return null;
    }

    public String getFeedBack(String jobDescription, String attachmentText) throws Exception {
        String attachmentSection = (attachmentText != null && !attachmentText.isBlank())
                ? "\n[Resume or Portfolio]\n" + attachmentText + "\n"
                : "";

        String prompt = """
                You are a senior hiring manager and career strategist.
                Analyze the job posting and the applicant's resume or portfolio,
                then provide a concrete job-fit report in Korean.

                [Job Posting]
                %s
                %s

                Important rules:
                - Do not evaluate a cover letter. This product does not collect cover letters.
                - Base the analysis only on the job posting and the attached resume/portfolio.
                - If required information is missing, say what is missing and how the applicant should supplement it.
                - Give a job fit score out of 10 with clear evidence.

                Respond in this exact format:

                ## 1. 채용공고 핵심 요약
                - 회사/서비스 이해:
                - 주요 업무:
                - 지원 자격:
                - 우대 사항:
                - 핵심 기술/역량 키워드:

                ## 2. 지원자 자료 요약
                - 경력/프로젝트:
                - 기술 스택:
                - 문제 해결 경험:
                - 협업/운영 경험:

                ## 3. 직무 적합도
                - 점수: /10
                - 점수 근거:
                - 강하게 매칭되는 요구사항:
                - 부족하거나 근거가 약한 요구사항:

                ## 4. SWOT 기반 지원 전략
                - Strength:
                - Weakness:
                - Opportunity:
                - Threat:

                ## 5. 보완해야 할 역량
                - 우선순위 1:
                - 우선순위 2:
                - 우선순위 3:

                ## 6. 차별화된 강점
                - 강점:
                - 공고와 연결하는 방법:

                ## 7. 이력서/포트폴리오 개선 제안
                - 추가하면 좋은 내용:
                - 더 강조해야 할 내용:
                - 줄이거나 정리하면 좋은 내용:

                ## 8. 최종 지원 전략
                """.formatted(jobDescription, attachmentSection);

        String body = """
                {
                  "contents": [
                    {
                      "parts": [
                        {"text": "%s"}
                      ]
                    }
                  ]
                }
                """.formatted(escapeJson(prompt));

        return callGemini(body);
    }

    public String getFeedBackWithVision(String jobDescription, List<String> base64Images) throws Exception {
        StringBuilder imageParts = new StringBuilder();
        for (String base64 : base64Images) {
            imageParts.append("""
                    ,{
                      "inline_data": {
                        "mime_type": "image/png",
                        "data": "%s"
                      }
                    }
                    """.formatted(base64));
        }

        String promptText = """
                You are a senior hiring manager and career strategist.
                Analyze the job posting and the attached resume or portfolio images,
                then provide a concrete job-fit report in Korean.

                [Job Posting]
                %s

                Use the attached images as the applicant's resume or portfolio.

                Important rules:
                - Do not evaluate a cover letter. This product does not collect cover letters.
                - Base the analysis only on the job posting and the attached resume/portfolio images.
                - If required information is missing, say what is missing and how the applicant should supplement it.
                - Give a job fit score out of 10 with clear evidence.

                Respond in this exact format:

                ## 1. 채용공고 핵심 요약
                - 회사/서비스 이해:
                - 주요 업무:
                - 지원 자격:
                - 우대 사항:
                - 핵심 기술/역량 키워드:

                ## 2. 지원자 자료 요약
                - 경력/프로젝트:
                - 기술 스택:
                - 문제 해결 경험:
                - 협업/운영 경험:

                ## 3. 직무 적합도
                - 점수: /10
                - 점수 근거:
                - 강하게 매칭되는 요구사항:
                - 부족하거나 근거가 약한 요구사항:

                ## 4. SWOT 기반 지원 전략
                - Strength:
                - Weakness:
                - Opportunity:
                - Threat:

                ## 5. 보완해야 할 역량
                - 우선순위 1:
                - 우선순위 2:
                - 우선순위 3:

                ## 6. 차별화된 강점
                - 강점:
                - 공고와 연결하는 방법:

                ## 7. 이력서/포트폴리오 개선 제안
                - 추가하면 좋은 내용:
                - 더 강조해야 할 내용:
                - 줄이거나 정리하면 좋은 내용:

                ## 8. 최종 지원 전략
                """.formatted(jobDescription);

        String body = """
                {
                  "contents": [
                    {
                      "parts": [
                        {"text": "%s"}
                        %s
                      ]
                    }
                  ]
                }
                """.formatted(escapeJson(promptText), imageParts);

        return callGemini(body);
    }

    private String callGemini(String body) throws Exception {
        String normalizedApiKey = apiKey == null ? "" : apiKey.trim();
        if (normalizedApiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is missing.");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + normalizedApiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = null;
        for (int attempt = 1; attempt <= MAX_GEMINI_ATTEMPTS; attempt++) {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (!isRetryableGeminiError(response.statusCode()) || attempt == MAX_GEMINI_ATTEMPTS) {
                break;
            }

            Thread.sleep(Duration.ofSeconds(attempt * 2).toMillis());
        }

        String responseBody = response.body();

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Gemini API request failed. status=" + response.statusCode() + ", body=" + responseBody);
        }

        JSONObject json = new JSONObject(responseBody);
        return json
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text");
    }

    private boolean isRetryableGeminiError(int statusCode) {
        return statusCode == 429 || statusCode == 503 || statusCode >= 500;
    }

    private String createCacheKey(FeedbackHistory history) {
        return createCacheKey(
                history.getJobDescription(),
                history.getAttachmentText(),
                history.getBase64Images()
        );
    }

    private String createCacheKey(String jobDescription, String attachmentText, String base64Images) {
        String raw = normalize(jobDescription)
                + "\n---attachment---\n" + normalize(attachmentText)
                + "\n---images---\n" + normalize(base64Images);
        return CACHE_PREFIX + sha256(raw);
    }

    private String toCsv(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
