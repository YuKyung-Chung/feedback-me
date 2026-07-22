package com.jyk.feedbackme.evaluation;

import com.jyk.feedbackme.FeedbackmeApplication;
import com.jyk.feedbackme.service.AnalysisOrchestrator;
import com.jyk.feedbackme.service.FileExtractService;
import com.jyk.feedbackme.service.OpenAiClient;
import com.jyk.feedbackme.analysis.OpenAiUsage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 동일 입력을 Single-step과 Harness로 반복 실행해 비교 자료를 생성합니다. */
public final class HarnessComparisonRunner {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final List<String> CASES = List.of("TC-01", "TC-02", "TC-03", "TC-04", "TC-05");
    private HarnessComparisonRunner() { }

    public static void main(String[] args) throws Exception {
        Path evaluation = Path.of("..").resolve("evaluation").toAbsolutePath().normalize();
        Path baseline = evaluation.resolve("baseline");
        int repetitions = Integer.parseInt(System.getenv().getOrDefault("EVALUATION_REPETITIONS", "3"));
        String runId = "comparison-" + OffsetDateTime.now(SEOUL).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path runRoot = evaluation.resolve("runs").resolve(runId);
        Files.createDirectories(runRoot);
        List<Map<String, Object>> results = new ArrayList<>();
        String key = resolveKey(evaluation.getParent().resolve(".env"));

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(FeedbackmeApplication.class)
                .profiles("test").web(WebApplicationType.NONE).run("--openai.api-key=" + key)) {
            FileExtractService extractor = context.getBean(FileExtractService.class);
            OpenAiClient client = context.getBean(OpenAiClient.class);
            AnalysisOrchestrator orchestrator = context.getBean(AnalysisOrchestrator.class);
            for (String id : CASES) {
                Path root = baseline.resolve(id);
                String job = readSnapshot(root.resolve("job-posting.txt"));
                for (String candidateName : List.of("candidate.docx", "candidate-personal.pdf")) {
                    Path candidate = root.resolve(candidateName);
                    if (!Files.isRegularFile(candidate)) continue;
                    String material = extract(extractor, candidate);
                    for (int repetition = 1; repetition <= repetitions; repetition++) {
                        results.add(run("single-step", id, candidateName, repetition, job, material, runRoot, client, null));
                        results.add(run("harness", id, candidateName, repetition, job, material, runRoot, client, orchestrator));
                    }
                }
            }
        }
        writeJson(runRoot.resolve("summary.json"), Map.of("runId", runId, "repetitions", repetitions, "aggregates", aggregate(results), "results", results));
        System.out.println("Comparison evaluation completed: " + runRoot);
    }

    private static Map<String, Object> run(String mode, String id, String filename, int repetition, String job, String material, Path root, OpenAiClient client, AnalysisOrchestrator orchestrator) {
        long started = System.nanoTime();
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("mode", mode); metric.put("testCaseId", id); metric.put("candidate", filename); metric.put("repetition", repetition);
        try {
            String result;
            OpenAiUsage usage;
            if (orchestrator == null) {
                result = client.analyze(job, material);
                usage = client.getLastUsage();
            } else {
                long[] input = {0}, cached = {0}, outputTokens = {0}; double[] cost = {0};
                result = orchestrator.analyze(job, material, (step, output) -> {
                    if (output != null && !output.isBlank()) {
                        OpenAiUsage current = client.getLastUsage();
                        input[0] += current.inputTokens(); cached[0] += current.cachedInputTokens(); outputTokens[0] += current.outputTokens(); cost[0] += current.estimatedCostUsd();
                    }
                });
                usage = new OpenAiUsage(input[0], cached[0], outputTokens[0], 0, "harness", cost[0]);
            }
            metric.put("status", "COMPLETED"); metric.put("inputTokens", usage.inputTokens()); metric.put("cachedInputTokens", usage.cachedInputTokens());
            metric.put("outputTokens", usage.outputTokens()); metric.put("costUsd", usage.estimatedCostUsd());
            Path dir = root.resolve(id); Files.createDirectories(dir);
            Files.writeString(dir.resolve(mode + "-" + filename + "-run-" + repetition + ".md"), result, StandardCharsets.UTF_8);
        } catch (Exception e) {
            metric.put("status", "FAILED"); metric.put("error", e.toString());
        }
        metric.put("durationSeconds", Duration.ofNanos(System.nanoTime() - started).toMillis() / 1000.0);
        return metric;
    }

    private static Map<String, Object> aggregate(List<Map<String, Object>> results) {
        Map<String, Object> aggregate = new LinkedHashMap<>();
        for (String mode : List.of("single-step", "harness")) {
            List<Map<String, Object>> rows = results.stream().filter(row -> mode.equals(row.get("mode"))).toList();
            double duration = rows.stream().mapToDouble(row -> ((Number) row.getOrDefault("durationSeconds", 0)).doubleValue()).average().orElse(0);
            double cost = rows.stream().mapToDouble(row -> ((Number) row.getOrDefault("costUsd", 0)).doubleValue()).average().orElse(0);
            long completed = rows.stream().filter(row -> "COMPLETED".equals(row.get("status"))).count();
            aggregate.put(mode, Map.of("runs", rows.size(), "successRate", rows.isEmpty() ? 0 : (double) completed / rows.size(), "averageDurationSeconds", duration, "averageCostUsd", cost));
        }
        return aggregate;
    }

    private static String extract(FileExtractService extractor, Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        MockMultipartFile file = new MockMultipartFile("file", path.getFileName().toString(), "application/octet-stream", bytes);
        return extractor.extract(file);
    }

    private static String readSnapshot(Path path) throws IOException {
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream().filter(line -> !line.startsWith("URL:"))
                .reduce((a, b) -> a + System.lineSeparator() + b).orElse("").trim();
    }

    private static String resolveKey(Path env) throws IOException {
        String value = System.getenv("OPENAI_API_KEY");
        if (value != null && !value.isBlank()) return value.trim();
        return Files.readAllLines(env, StandardCharsets.UTF_8).stream().filter(line -> line.startsWith("OPENAI_API_KEY="))
                .map(line -> line.substring("OPENAI_API_KEY=".length()).trim()).findFirst()
                .orElseThrow(() -> new IllegalStateException("OPENAI_API_KEY is missing."));
    }

    private static void writeJson(Path path, Object value) throws IOException {
        Files.writeString(path, new org.json.JSONObject(value).toString(2), StandardCharsets.UTF_8);
    }
}
