package com.jyk.feedbackme.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 하네스 단계 실행·실패·재시도·비용을 Prometheus 메트릭으로 기록합니다. */
@Component
public class HarnessMetrics {
    private final MeterRegistry registry;

    public HarnessMetrics(MeterRegistry registry) { this.registry = registry; }

    public Timer.Sample startStep() { return Timer.start(registry); }

    public void recordStep(Timer.Sample sample, String step) {
        sample.stop(registry.timer("feedbackme_analysis_step_duration_seconds", "step", step));
    }

    public void recordFailure(String step) {
        Counter.builder("feedbackme_analysis_step_failures_total").tag("step", step).register(registry).increment();
    }

    public void recordRetry(String step) {
        Counter.builder("feedbackme_analysis_step_retries_total").tag("step", step).register(registry).increment();
    }

    public void recordCost(double costUsd) {
        registry.counter("feedbackme_openai_cost_total").increment(Math.max(0, costUsd));
    }

    public void recordRequest(String model) { Counter.builder("feedbackme_openai_requests_total").tag("model", model).register(registry).increment(); }
    public void recordTokens(String model, long input, long output) {
        registry.counter("feedbackme_openai_tokens_total", "model", model, "type", "input").increment(input);
        registry.counter("feedbackme_openai_tokens_total", "model", model, "type", "output").increment(output);
    }
    public void recordCompleted() { registry.counter("feedbackme_analysis_completed_total").increment(); }
    public void recordFailed() { registry.counter("feedbackme_analysis_failed_total").increment(); }
    public void recordCheckpointRecovered() { registry.counter("feedbackme_analysis_checkpoint_recovered_total").increment(); }
    public void recordQueueWait(double seconds) { registry.timer("feedbackme_queue_wait_seconds").record(java.time.Duration.ofMillis((long) (seconds * 1000))); }
}
