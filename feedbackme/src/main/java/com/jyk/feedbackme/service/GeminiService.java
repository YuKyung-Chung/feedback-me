package com.jyk.feedbackme.service;

import com.jyk.feedbackme.domain.FeedbackHistory;
import com.jyk.feedbackme.domain.FeedbackStatus;
import com.jyk.feedbackme.repository.FeedbackHistoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Service
public class GeminiService {

    @Value("${gemini.api-key}")
    private String apiKey;

    private final FeedbackHistoryRepository feedbackHistoryRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String QUEUE_KEY = "feedback:queue";

    public GeminiService(FeedbackHistoryRepository feedbackHistoryRepository, RedisTemplate<String, String> redisTemplate) {
        this.feedbackHistoryRepository = feedbackHistoryRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public Long enqueueFeedbackRequest(String jobDescription, String coverLetter, String attachmentText, List<String> base64Images) {
        String imagesCsv = null;
        if (base64Images != null && !base64Images.isEmpty()) {
            imagesCsv = String.join(",", base64Images);
        }

        // 1. 접수 창구에서 최초 1회만 DB 저장 수행
        FeedbackHistory history = FeedbackHistory.builder()
                .jobDescription(jobDescription)
                .coverLetter(coverLetter)
                .attachmentText(attachmentText)
                .base64Images(imagesCsv)
                .status(FeedbackStatus.PENDING)
                .build();

        feedbackHistoryRepository.save(history);

        // 2. Redis 큐에 ID 삽입
        redisTemplate.opsForList().rightPush(QUEUE_KEY, history.getId().toString());

        return history.getId();
    }

    // [수정] 외부에서 조회한 history 객체를 파라미터로 받도록 변경, 신규 save 로직 제거
    @Transactional
    public String getFeedBack(String jobDescription, String coverLetter, String attachmentText, FeedbackHistory history) throws Exception {
        String attachmentSection = (attachmentText != null && !attachmentText.isBlank())
                ? "\n[이력서 / 포트폴리오]\n" + attachmentText + "\n\n위 첨부 파일 내용도 반드시 참고하여 피드백해주세요."
                : "";

        String prompt = """
                당신은 채용 전문가입니다. 아래 채용공고และ 자기소개서를 분석하여 다음 항목별로 구체적인 피드백을 제공해주세요.
                
                [채용 공고]
                %s
                
                [자기소개서]
                %s
                %s
                
                다음 형식으로 답변해주세요:
                
                ## 1. 직무 적합성 (채용공고 요구사항과 자소서 매칭도)
                - 잘된 점:
                - 부족한 점:
                - 개선 제안:
                
                ## 2. 표현 및 문장력
                - 잘된 점:
                - 부족한 점:
                - 개선 제안:
                
                ## 3. 논리성 및 구성
                - 잘된 점:
                - 부족한 점:
                - 개선 제안:
                
                ## 4. 차별화 포인트
                - 잘된 점:
                - 부족한 점:
                - 개선 제안:
                
                ## 5. 종합 의견 및 우선 개선사항
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
                """.formatted(prompt.replace("\"", "\\\"").replace("\n", "\\n"));

        return callGemini(body, history);
    }

    // [수정] 외부에서 조회한 history 객체를 파라미터로 받도록 변경, 신규 save 로직 제거
    @Transactional
    public String getFeedBackWithVision(String jobDescription, String coverLetter, List<String> base64Images, FeedbackHistory history) throws Exception {
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
                당신은 채용 전문가입니다. 아래 채용공고와 자기소개서, 그리고 첨부된 이력서/포트폴리오 이미지를 분석하여 항목별로 구체적인 피드백을 제공해주세요.
                
                [채용 공고]
                %s
                
                [자기소개서]
                %s
                
                첨부된 이미지는 지원자의 이력서 및 포트폴리오입니다. 반드시 참고하여 피드백해주세요.
                
                다음 형식으로 답변해주세요:
                
                ## 1. 직무 적합성 (채용공고 요구사항과 자소서 매칭도)
                - 잘된 점:
                - 부족한 점:
                - 개선 제안:
                
                ## 2. 표현 및 문장력
                - 잘된 점:
                - 부족한 점:
                - 개선 제안:
                
                ## 3. 논리성 및 구성
                - 잘된 점:
                - 부족한 점:
                - 개선 제안:
                
                ## 4. 차별화 포인트
                - 잘된 점:
                - 부족한 점:
                - 개선 제안:
                
                ## 5. 종합 의견 및 우선 개선사항
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
                """.formatted(
                promptText.replace("\"", "\\\"").replace("\n", "\\n"),
                imageParts
        );

        return callGemini(body, history);
    }

    // 공통 Gemini API 호출 (전달받은 원래 history 객체의 내용을 채워줌)
    private String callGemini(String body, FeedbackHistory history) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();

        org.json.JSONObject json = new org.json.JSONObject(responseBody);
        String text = json
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text");

        // 호출 성공 시, 처음 만들어졌던 원래 history 엔티티 내부 필드에 값을 세팅
        history.completeFeedback(text);
        return text;
    }
}