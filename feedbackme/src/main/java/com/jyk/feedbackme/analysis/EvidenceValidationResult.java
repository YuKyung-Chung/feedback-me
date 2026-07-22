package com.jyk.feedbackme.analysis;

import java.util.List;

/** 모델 응답에 포함된 근거 ID의 검증 결과입니다. */
public record EvidenceValidationResult(List<String> referencedChunkIds, List<String> invalidChunkIds) {
    public boolean isValid() { return invalidChunkIds == null || invalidChunkIds.isEmpty(); }
}
