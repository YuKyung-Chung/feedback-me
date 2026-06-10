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

@Service
public class GeminiService {

    @Value("${gemini.api-key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final FeedbackHistoryRepository feedbackHistoryRepository;

    //생성자 주입
    public GeminiService(FeedbackHistoryRepository feedbackHistoryRepository){
        this.feedbackHistoryRepository = feedbackHistoryRepository;
    }

    @Transactional
    public String getFeedBack(String jobDescription, String coverLetter) throws Exception{
        // 1. 초기 상태(PENDING)로 이력 저장
        FeedbackHistory history = FeedbackHistory.builder()
                .jobDescription(jobDescription)
                .coverLetter(coverLetter)
                .status(FeedbackStatus.PENDING)
                .build();
        feedbackHistoryRepository.save(history);

        // 2. 프롬프트 조립 (%s 누락 부분 수정 반영)
        String prompt = """
            당신은 채용 전문가입니다. 아래 채용공고와 자기소개서를 분석하여 다음 항목별로 구체적인 피드백을 제공해주세요.
            
            [채용 공고]
            %s
            
            [자기소개서]
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
            """.formatted(jobDescription, coverLetter);

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

        // 3. Gemini API 호출
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            // 4. JSON에서 텍스트만 추출
            org.json.JSONObject json = new org.json.JSONObject(responseBody);
            String text = json
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

            // 5. 호출 성공 시 결과 업데이트 및 상태 변경 (COMPLETED)
            history.completeFeedback(text);
            return text;

        } catch (Exception e) {
            // 호출 실패 시 상태 변경 (FAILED)
            history.failFeedback();
            throw e;
        }
    }
}