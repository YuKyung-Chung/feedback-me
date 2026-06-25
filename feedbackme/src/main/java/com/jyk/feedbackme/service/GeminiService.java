package com.jyk.feedbackme.service;

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

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${feedback.cache.result-ttl-seconds:604800}")
    private long resultCacheTtlSeconds;

    private final FeedbackHistoryRepository feedbackHistoryRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public GeminiService(FeedbackHistoryRepository feedbackHistoryRepository, RedisTemplate<String, String> redisTemplate) {
        this.feedbackHistoryRepository = feedbackHistoryRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public Long enqueueFeedbackRequest(String jobDescription, String coverLetter, String attachmentText, List<String> base64Images) {
        String imagesCsv = toCsv(base64Images);
        String cacheKey = createCacheKey(jobDescription, coverLetter, attachmentText, imagesCsv);
        String cachedResult = redisTemplate.opsForValue().get(cacheKey);

        FeedbackHistory history = FeedbackHistory.builder()
                .jobDescription(jobDescription)
                .coverLetter(coverLetter)
                .attachmentText(attachmentText)
                .base64Images(imagesCsv)
                .status(FeedbackStatus.PENDING)
                .build();

        if (cachedResult != null) {
            history.completeFeedback(cachedResult);
            feedbackHistoryRepository.save(history);
            return history.getId();
        }

        feedbackHistoryRepository.save(history);
        redisTemplate.opsForList().rightPush(QUEUE_KEY, history.getId().toString());
        return history.getId();
    }

    public String getCachedResult(FeedbackHistory history) {
        return redisTemplate.opsForValue().get(createCacheKey(history));
    }

    public void cacheResult(FeedbackHistory history, String result) {
        redisTemplate.opsForValue().set(createCacheKey(history), result, Duration.ofSeconds(resultCacheTtlSeconds));
    }

    public String getFeedBack(String jobDescription, String coverLetter, String attachmentText) throws Exception {
        String attachmentSection = (attachmentText != null && !attachmentText.isBlank())
                ? "\n[Attachment]\n" + attachmentText + "\n\nPlease use the attachment content in your feedback."
                : "";

        String prompt = """
                You are a hiring expert. Analyze the job posting and cover letter below,
                then provide concrete feedback in Korean.

                [Job Posting]
                %s

                [Cover Letter]
                %s
                %s

                Respond in this format:

                ## 1. Job fit
                - Strengths:
                - Gaps:
                - Suggestions:

                ## 2. Expression and sentences
                - Strengths:
                - Gaps:
                - Suggestions:

                ## 3. Logic and structure
                - Strengths:
                - Gaps:
                - Suggestions:

                ## 4. Differentiation
                - Strengths:
                - Gaps:
                - Suggestions:

                ## 5. Overall opinion and priorities
                """.formatted(jobDescription, coverLetter, attachmentSection);

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

    public String getFeedBackWithVision(String jobDescription, String coverLetter, List<String> base64Images) throws Exception {
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
                You are a hiring expert. Analyze the job posting, cover letter,
                and attached resume or portfolio images, then provide concrete feedback in Korean.

                [Job Posting]
                %s

                [Cover Letter]
                %s

                Use the attached images as supporting context.

                Respond in this format:

                ## 1. Job fit
                - Strengths:
                - Gaps:
                - Suggestions:

                ## 2. Expression and sentences
                - Strengths:
                - Gaps:
                - Suggestions:

                ## 3. Logic and structure
                - Strengths:
                - Gaps:
                - Suggestions:

                ## 4. Differentiation
                - Strengths:
                - Gaps:
                - Suggestions:

                ## 5. Overall opinion and priorities
                """.formatted(jobDescription, coverLetter);

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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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

    private String createCacheKey(FeedbackHistory history) {
        return createCacheKey(
                history.getJobDescription(),
                history.getCoverLetter(),
                history.getAttachmentText(),
                history.getBase64Images()
        );
    }

    private String createCacheKey(String jobDescription, String coverLetter, String attachmentText, String base64Images) {
        String raw = normalize(jobDescription)
                + "\n---cover-letter---\n" + normalize(coverLetter)
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
