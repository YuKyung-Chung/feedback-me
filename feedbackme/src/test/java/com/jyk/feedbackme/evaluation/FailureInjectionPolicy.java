package com.jyk.feedbackme.evaluation;

import com.jyk.feedbackme.analysis.AnalysisStep;
import com.jyk.feedbackme.service.FailureInjector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 평가 테스트에서만 사용하는 결정적 장애 주입기입니다. */
@Component
public class FailureInjectionPolicy implements FailureInjector {
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

    @Override
    public String beforeCall(AnalysisStep step) throws Exception {
        String name = step.name();
        if (consume(name + ":TIMEOUT")) throw new HttpTimeoutException("Injected timeout at " + name);
        if (consume(name + ":SCHEMA")) return "{}";
        if (consume(name + ":EVIDENCE")) return "{\"matches\":[],\"evidenceChunkIds\":[\"invalid-999\"]}";
        return null;
    }

    private boolean consume(String key) {
        AtomicInteger count = remaining.get(key);
        return count != null && count.getAndUpdate(value -> Math.max(0, value - 1)) > 0;
    }
}
