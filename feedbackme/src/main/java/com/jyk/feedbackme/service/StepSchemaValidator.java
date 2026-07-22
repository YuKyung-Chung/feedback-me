package com.jyk.feedbackme.service;

import com.jyk.feedbackme.analysis.AnalysisStep;
import com.jyk.feedbackme.analysis.StepSchemaValidationResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 분석 단계별 JSON 응답의 최소 계약을 검증합니다. */
@Component
public class StepSchemaValidator {
    public StepSchemaValidationResult validate(AnalysisStep step, String output) {
        List<String> errors = new ArrayList<>();
        if (output == null || output.isBlank()) return new StepSchemaValidationResult(false, List.of("응답이 비어 있습니다."));
        if (step == AnalysisStep.REPORT) return new StepSchemaValidationResult(true, errors);
        String json = unwrap(output);
        try {
            JSONObject object = new JSONObject(json);
            switch (step) {
                case JOB_ANALYSIS -> require(object, errors, "position", "responsibilities", "requiredSkills", "preferredSkills");
                case CANDIDATE_ANALYSIS -> requireArray(object, errors, "experiences");
                case MATCHING -> requireArray(object, errors, "matches");
                case GAP_ANALYSIS -> requireArray(object, errors, "gaps");
                case VERIFICATION -> require(object, errors, "valid", "issues", "correctedReport");
                default -> { }
            }
        } catch (Exception e) {
            errors.add("유효한 JSON 객체가 아닙니다: " + e.getMessage());
        }
        return new StepSchemaValidationResult(errors.isEmpty(), errors);
    }

    private void require(JSONObject object, List<String> errors, String... names) {
        for (String name : names) if (!object.has(name)) errors.add("필수 필드 누락: " + name);
    }
    private void requireArray(JSONObject object, List<String> errors, String name) {
        if (!object.has(name)) errors.add("필수 배열 누락: " + name);
        else if (!(object.get(name) instanceof JSONArray)) errors.add("배열 형식 오류: " + name);
    }
    private String unwrap(String output) {
        String value = output.trim();
        if (value.startsWith("```")) value = value.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        return value.trim();
    }
}
