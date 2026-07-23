package com.jyk.feedbackme.evaluation;

import com.jyk.feedbackme.analysis.AnalysisStep;
import com.jyk.feedbackme.config.HarnessMetrics;
import com.jyk.feedbackme.service.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.http.HttpTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** 외부 API 비용 없이 하네스의 오류 복구 시나리오를 검증합니다. */
class FailureScenarioTest {
    @Test
    void schemaErrorRetriesOnlyTheFailedStep() throws Exception {
        OpenAiClient client = Mockito.mock(OpenAiClient.class);
        AtomicInteger matchingCalls = new AtomicInteger();
        stubNormalResponses(client, matchingCalls, false);
        AnalysisOrchestrator orchestrator = orchestrator(client);

        String result = orchestrator.analyze("job", "candidate");

        assertEquals("verified", result);
        assertEquals(2, matchingCalls.get());
    }

    @Test
    void timeoutTwiceThenSucceeds() throws Exception {
        OpenAiClient client = Mockito.mock(OpenAiClient.class);
        AtomicInteger reportCalls = new AtomicInteger();
        when(client.analyzeStep(eq(AnalysisStep.REPORT), any())).thenAnswer(invocation -> {
            if (reportCalls.incrementAndGet() <= 2) throw new HttpTimeoutException("injected timeout");
            return "report";
        });
        stubNormalResponsesExceptReport(client);
        AnalysisOrchestrator orchestrator = orchestrator(client);

        assertEquals("verified", orchestrator.analyze("job", "candidate"));
        assertEquals(3, reportCalls.get());
    }

    @Test
    void invalidEvidenceRetriesOnlyTheMatchingStep() throws Exception {
        OpenAiClient client = Mockito.mock(OpenAiClient.class);
        AtomicInteger matchingCalls = new AtomicInteger();
        when(client.analyzeStep(eq(AnalysisStep.JOB_ANALYSIS), any())).thenReturn("{\"position\":\"x\",\"responsibilities\":[],\"requiredSkills\":[],\"preferredSkills\":[]}");
        when(client.analyzeStep(eq(AnalysisStep.CANDIDATE_ANALYSIS), any())).thenReturn("{\"experiences\":[]}");
        when(client.analyzeStep(eq(AnalysisStep.MATCHING), any())).thenAnswer(i -> matchingCalls.incrementAndGet() == 1 ? "{\"matches\":[],\"evidenceChunkIds\":[\"invalid-999\"]}" : "{\"matches\":[]}");
        when(client.analyzeStep(eq(AnalysisStep.GAP_ANALYSIS), any())).thenReturn("{\"gaps\":[]}");
        when(client.analyzeStep(eq(AnalysisStep.REPORT), any())).thenReturn("report");
        when(client.analyzeStep(eq(AnalysisStep.VERIFICATION), any())).thenReturn("{\"valid\":true,\"issues\":[],\"correctedReport\":\"verified\"}");

        assertEquals("verified", orchestrator(client).analyze("job", "candidate"));
        assertEquals(2, matchingCalls.get());
    }

    private AnalysisOrchestrator orchestrator(OpenAiClient client) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PromptLoader prompts = Mockito.mock(PromptLoader.class);
        try { when(prompts.load(any(), any(), any())).thenReturn("test prompt"); } catch (Exception ignored) { }
        return new AnalysisOrchestrator(prompts, client, new DocumentChunker(), new EvidenceValidator(),
                new StepSchemaValidator(), new AnalysisRetryPolicy(), new HarnessMetrics(registry));
    }

    private void stubNormalResponses(OpenAiClient client, AtomicInteger matchingCalls, boolean ignored) throws Exception {
        when(client.analyzeStep(eq(AnalysisStep.JOB_ANALYSIS), any())).thenReturn("{\"position\":\"x\",\"responsibilities\":[],\"requiredSkills\":[],\"preferredSkills\":[]}");
        when(client.analyzeStep(eq(AnalysisStep.CANDIDATE_ANALYSIS), any())).thenReturn("{\"experiences\":[]}");
        when(client.analyzeStep(eq(AnalysisStep.MATCHING), any())).thenAnswer(i -> matchingCalls.incrementAndGet() == 1 ? "{}" : "{\"matches\":[]}");
        when(client.analyzeStep(eq(AnalysisStep.GAP_ANALYSIS), any())).thenReturn("{\"gaps\":[]}");
        when(client.analyzeStep(eq(AnalysisStep.REPORT), any())).thenReturn("report");
        when(client.analyzeStep(eq(AnalysisStep.VERIFICATION), any())).thenReturn("{\"valid\":true,\"issues\":[],\"correctedReport\":\"verified\"}");
    }

    private void stubNormalResponsesExceptReport(OpenAiClient client) throws Exception {
        when(client.analyzeStep(eq(AnalysisStep.JOB_ANALYSIS), any())).thenReturn("{\"position\":\"x\",\"responsibilities\":[],\"requiredSkills\":[],\"preferredSkills\":[]}");
        when(client.analyzeStep(eq(AnalysisStep.CANDIDATE_ANALYSIS), any())).thenReturn("{\"experiences\":[]}");
        when(client.analyzeStep(eq(AnalysisStep.MATCHING), any())).thenReturn("{\"matches\":[]}");
        when(client.analyzeStep(eq(AnalysisStep.GAP_ANALYSIS), any())).thenReturn("{\"gaps\":[]}");
        when(client.analyzeStep(eq(AnalysisStep.VERIFICATION), any())).thenReturn("{\"valid\":true,\"issues\":[],\"correctedReport\":\"verified\"}");
    }
}
