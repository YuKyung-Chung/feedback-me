package com.jyk.feedbackme.service;

import com.jyk.feedbackme.analysis.DocumentChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 추출된 원문을 재현 가능한 ID가 있는 청크로 나누는 컴포넌트입니다. */
@Component
public class DocumentChunker {
    private static final int DEFAULT_MAX_LENGTH = 800;

    /** 문단 경계를 우선 사용하고, 긴 문단은 일정 길이로 잘라 청크를 만듭니다. */
    public List<DocumentChunk> chunk(String source, String text) {
        if (text == null || text.isBlank()) return List.of();
        List<DocumentChunk> result = new ArrayList<>();
        int sequence = 1;
        for (String paragraph : text.replace("\r\n", "\n").split("\\n\\s*\\n")) {
            String normalized = paragraph.trim().replaceAll("\\s+", " ");
            for (int start = 0; start < normalized.length(); start += DEFAULT_MAX_LENGTH) {
                String part = normalized.substring(start, Math.min(start + DEFAULT_MAX_LENGTH, normalized.length())).trim();
                if (!part.isBlank()) result.add(new DocumentChunk(source + "-" + String.format("%03d", sequence), source, sequence++, part));
            }
        }
        return result;
    }

    /** 모델 프롬프트에 삽입할 수 있도록 chunkId와 원문을 함께 직렬화합니다. */
    public String formatForPrompt(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) return "(근거 문서가 없습니다.)";
        return chunks.stream().map(c -> "[" + c.chunkId() + "] " + c.text()).reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
