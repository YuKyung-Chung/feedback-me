package com.jyk.feedbackme.analysis;

/** 문서에서 추출한 근거 단위입니다. 모델은 문장을 새로 만들기보다 chunkId를 근거로 반환해야 합니다. */
public record DocumentChunk(String chunkId, String source, int sequence, String text) {
    public DocumentChunk {
        if (chunkId == null || chunkId.isBlank()) throw new IllegalArgumentException("chunkId는 필수입니다.");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("source는 필수입니다.");
        if (text == null || text.isBlank()) throw new IllegalArgumentException("text는 비어 있을 수 없습니다.");
    }
}
