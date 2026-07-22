package com.jyk.feedbackme.service;

import com.jyk.feedbackme.analysis.AnalysisStep;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.Map;
import java.util.EnumMap;
import org.json.JSONArray;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 저장된 단계 결과를 해석해 분석 재개 지점을 계산합니다. */
@Component
public class AnalysisCheckpointService {
    private static final Pattern STEP = Pattern.compile("\\\"step\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    public Set<AnalysisStep> completedSteps(String stepResultsJson) {
        Set<AnalysisStep> result = EnumSet.noneOf(AnalysisStep.class);
        if (stepResultsJson == null || stepResultsJson.isBlank()) return result;
        Matcher matcher = STEP.matcher(stepResultsJson);
        while (matcher.find()) {
            try { result.add(AnalysisStep.valueOf(matcher.group(1))); }
            catch (IllegalArgumentException ignored) { }
        }
        return result;
    }

    public AnalysisStep nextStep(String stepResultsJson) {
        Set<AnalysisStep> completed = completedSteps(stepResultsJson);
        for (AnalysisStep step : AnalysisStep.values()) {
            if (step != AnalysisStep.VERIFICATION && !completed.contains(step)) return step;
        }
        return AnalysisStep.VERIFICATION;
    }

    public Map<AnalysisStep, String> restoreResults(String stepResultsJson) {
        Map<AnalysisStep, String> results = new EnumMap<>(AnalysisStep.class);
        if (stepResultsJson == null || stepResultsJson.isBlank()) return results;
        try {
            JSONArray array = new JSONArray(stepResultsJson);
            for (int i = 0; i < array.length(); i++) {
                var item = array.getJSONObject(i);
                results.put(AnalysisStep.valueOf(item.getString("step")), item.getString("result"));
            }
        } catch (Exception ignored) { }
        return results;
    }
}
