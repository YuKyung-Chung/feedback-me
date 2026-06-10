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
                다음 채용 공고와 자소서를 분석해서 개선점을 알려줘.
                
                [채용 공고]
                %s
                
                [자기소개서]
                %s
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

            // 4. 호출 성공 시 결과 업데이트 및 상태 변경 (COMPLETED)
            history.completeFeedback(responseBody);
            return responseBody;

        } catch (Exception e) {
            // 호출 실패 시 상태 변경 (FAILED)
            history.failFeedback();
            throw e;
        }
    }
}