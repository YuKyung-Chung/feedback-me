package com.jyk.feedbackme.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private static final String RESPONSES_API_URL = "https://api.openai.com/v1/responses";
    private static final int MAX_ATTEMPTS = 3;

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.models.report:gpt-5.6-terra}")
    private String legacyFeedbackModel;

    @Value("${feedback.analysis.prompt-version:v1.0.0}")
    private String promptVersion;

    private final PromptLoader promptLoader;
    private final HttpClient httpClient;

    @Autowired
    public OpenAiClient(PromptLoader promptLoader) {
        this(promptLoader, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
    }

    OpenAiClient(PromptLoader promptLoader, HttpClient httpClient) {
        this.promptLoader = promptLoader;
        this.httpClient = httpClient;
    }

    public String analyze(String jobDescription, String attachmentText) throws Exception {
        String prompt = promptLoader.load("legacy-feedback", promptVersion, Map.of(
                "jobPosting", jobDescription,
                "candidateMaterial", attachmentText == null || attachmentText.isBlank()
                        ? "No candidate material was provided."
                        : attachmentText,
                "candidateInputType", "Candidate resume or portfolio text"
        ));
        return callResponsesApi(textContent(prompt));
    }

    public String analyzeWithVision(String jobDescription, List<String> base64Images) throws Exception {
        String prompt = promptLoader.load("legacy-feedback", promptVersion, Map.of(
                "jobPosting", jobDescription,
                "candidateMaterial", "Use only information verifiable in the attached images.",
                "candidateInputType", "Candidate resume or portfolio images"
        ));

        JSONArray content = textContent(prompt);
        for (String base64Image : base64Images) {
            content.put(new JSONObject()
                    .put("type", "input_image")
                    .put("image_url", "data:image/png;base64," + base64Image)
                    .put("detail", "high"));
        }
        return callResponsesApi(content);
    }

    private JSONArray textContent(String prompt) {
        return new JSONArray().put(new JSONObject()
                .put("type", "input_text")
                .put("text", prompt));
    }

    private String callResponsesApi(JSONArray content) throws Exception {
        String normalizedApiKey = apiKey == null ? "" : apiKey.trim();
        if (normalizedApiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is missing. Set OPENAI_API_KEY.");
        }

        JSONObject body = new JSONObject()
                .put("model", legacyFeedbackModel)
                .put("reasoning", new JSONObject().put("effort", "low"))
                .put("input", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("content", content)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RESPONSES_API_URL))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + normalizedApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = null;
        long startedAt = System.nanoTime();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (!isRetryable(response.statusCode()) || attempt == MAX_ATTEMPTS) {
                break;
            }
            Duration delay = retryDelay(response, attempt);
            log.warn("Retrying OpenAI request. model={}, attempt={}, status={}, delayMs={}",
                    legacyFeedbackModel, attempt, response.statusCode(), delay.toMillis());
            Thread.sleep(delay.toMillis());
        }

        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
            String responseBody = response == null ? "" : response.body();
            int statusCode = response == null ? 0 : response.statusCode();
            throw new IllegalStateException("OpenAI API request failed. status=" + statusCode + ", body=" + responseBody);
        }

        JSONObject json = new JSONObject(response.body());
        JSONObject usage = json.optJSONObject("usage");
        log.info("OpenAI analysis completed. model={}, durationMs={}, inputTokens={}, outputTokens={}",
                legacyFeedbackModel, durationMs,
                usage == null ? 0 : usage.optInt("input_tokens"),
                usage == null ? 0 : usage.optInt("output_tokens"));
        return extractOutputText(json);
    }

    private String extractOutputText(JSONObject response) {
        String outputText = response.optString("output_text", "");
        if (!outputText.isBlank()) {
            return outputText;
        }

        JSONArray output = response.optJSONArray("output");
        if (output != null) {
            for (int index = 0; index < output.length(); index++) {
                JSONArray responseContent = output.getJSONObject(index).optJSONArray("content");
                if (responseContent == null) {
                    continue;
                }
                for (int contentIndex = 0; contentIndex < responseContent.length(); contentIndex++) {
                    JSONObject item = responseContent.getJSONObject(contentIndex);
                    if ("output_text".equals(item.optString("type"))) {
                        return item.getString("text");
                    }
                }
            }
        }
        throw new IllegalStateException("OpenAI response did not contain output text.");
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode == 503 || statusCode >= 500;
    }

    private Duration retryDelay(HttpResponse<String> response, int attempt) {
        String retryAfter = response.headers().firstValue("retry-after").orElse("");
        try {
            return Duration.ofSeconds(Long.parseLong(retryAfter));
        } catch (NumberFormatException ignored) {
            return Duration.ofSeconds(attempt * 2L);
        }
    }
}
