package com.jyk.feedbackme.service;

import com.jyk.feedbackme.domain.FeedbackHistory;
import com.jyk.feedbackme.domain.FeedbackStatus;
import com.jyk.feedbackme.repository.FeedbackHistoryRepository;
import org.springframework.beans.factory.annotation.Value;
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

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final FeedbackHistoryRepository feedbackHistoryRepository;

    public GeminiService(FeedbackHistoryRepository feedbackHistoryRepository) {
        this.feedbackHistoryRepository = feedbackHistoryRepository;
    }

    // 텍스트 기반 피드백 (파일 없거나 텍스트 추출 가능한 경우)
    @Transactional
    public String getFeedBack(String jobDescription, String coverLetter, String attachmentText) throws Exception {
        FeedbackHistory history = FeedbackHistory.builder()
                .jobDescription(jobDescription)
                .coverLetter(coverLetter)
                .status(FeedbackStatus.PENDING)
                .build();
        feedbackHistoryRepository.save(history);

        String attachmentSection = (attachmentText != null && !attachmentText.isBlank())
                ? "\n[이력서 / 포트폴리오]\n" + attachmentText + "\n\n위 첨부 파일 내용도 반드시 참고하여 피드백해주세요."
                : "";

        String prompt = """
                당신은 채용 전문가입니다. 아래 채용공고와 자기소개서를 분석하여 다음 항목별로 구체적인 피드백을 제공해주세요.
                
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

    // Vision 기반 피드백 (이미지 기반 PDF인 경우)
    @Transactional
    public String getFeedBackWithVision(String jobDescription, String coverLetter, List<String> base64Images) throws Exception {
        FeedbackHistory history = FeedbackHistory.builder()
                .jobDescription(jobDescription)
                .coverLetter(coverLetter)
                .status(FeedbackStatus.PENDING)
                .build();
        feedbackHistoryRepository.save(history);

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

    // 공통 Gemini API 호출
    private String callGemini(String body, FeedbackHistory history) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
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

            history.completeFeedback(text);
            return text;

        } catch (Exception e) {
            history.failFeedback();
            throw e;
        }
    }
}