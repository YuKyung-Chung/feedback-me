package com.jyk.feedbackme.analysis;

import java.util.List;

/** 단계별 응답 스키마 검증 결과입니다. */
public record StepSchemaValidationResult(boolean valid, List<String> errors) { }
