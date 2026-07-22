package com.jyk.feedbackme.service;

import com.jyk.feedbackme.analysis.DocumentChunk;
import com.jyk.feedbackme.analysis.EvidenceValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 모델 응답에 표시된 evidenceChunkIds가 실제 입력 청크인지 검증합니다. */
@Component
public class EvidenceValidator {
    private static final Pattern CHUNK_ID = Pattern.compile("\\\"(?:evidenceChunkIds|jobEvidenceChunkIds|candidateEvidenceChunkIds)\\\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    private static final Pattern ID = Pattern.compile("\\\"([^\\\"]+)\\\"");

    public EvidenceValidationResult validate(String modelOutput, List<DocumentChunk> chunks) {
        Set<String> validIds = chunks.stream().map(DocumentChunk::chunkId).collect(java.util.stream.Collectors.toSet());
        Set<String> referenced = new LinkedHashSet<>();
        Set<String> invalid = new LinkedHashSet<>();
        Matcher groups = CHUNK_ID.matcher(modelOutput == null ? "" : modelOutput);
        while (groups.find()) {
            Matcher ids = ID.matcher(groups.group(1));
            while (ids.find()) {
                String id = ids.group(1);
                referenced.add(id);
                if (!validIds.contains(id)) invalid.add(id);
            }
        }
        return new EvidenceValidationResult(new ArrayList<>(referenced), new ArrayList<>(invalid));
    }
}
