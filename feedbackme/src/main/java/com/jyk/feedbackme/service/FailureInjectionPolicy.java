package com.jyk.feedbackme.service;

import com.jyk.feedbackme.analysis.AnalysisStep;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 테스트 프로파일에서만 사용할 수 있는 결정적 장애 주입 정책입니다. 기본값은 비활성화입니다. */
@Component
public class FailureInjectionPolicy {
    private final Map<String, AtomicInteger> remaining = new ConcurrentHashMap<>();

    public FailureInjectionPolicy(@Value("${feedback.evaluation.failure-injection:}") String configuration) {
        if (configuration == null || configuration.isBlank()) return;
        for (String item : configuration.split(",")) {
            String[] parts = item.trim().split(":");
            if (parts.length == 3) {
                try { remaining.put(parts[0].trim().toUpperCase() + ":" + parts[1].trim().toUpperCase(), new AtomicInteger(Integer.parseInt(parts[2].trim()))); }
                catch (NumberFormatException ignored) { }
            }
        }
    }

    /** 주입 대상이면 예외를 던지고, 가짜 응답 대상이면 해당 응답을 반환합니다. */
    public String beforeCall(AnalysisStep step) throws Exception {
        String stepName = step.name();
        if (consume(stepName + ":TIMEOUT")) throw new HttpTimeoutException("Injected timeout at " + stepName);
        if (consume(stepName + ":SCHEMA")) return "{}";
        if (consume(stepName + ":EVIDENCE")) return "{\"matches\":[],\"evidenceChunkIds\":[\"invalid-999\"]}";
        return null;
    }

    private boolean consume(String key) {
        AtomicInteger count = remaining.get(key);
        return count != null && count.getAndUpdate(value -> Math.max(0, value - 1)) > 0;
    }
}
