package com.jyk.feedbackme.evaluation;

import com.jyk.feedbackme.FeedbackmeApplication;
import com.jyk.feedbackme.dto.CrawledJobPosting;
import com.jyk.feedbackme.service.CrawlingService;
import com.jyk.feedbackme.service.FileExtractService;
import com.jyk.feedbackme.service.GeminiService;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BaselineEvaluationRunner {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final List<String> CASE_IDS = List.of("TC-01", "TC-02", "TC-03", "TC-04", "TC-05");

    private BaselineEvaluationRunner() {
    }

    public static void main(String[] args) throws Exception {
        Path evaluationRoot = Path.of("..", "evaluation").toAbsolutePath().normalize();
        Path baselineRoot = evaluationRoot.resolve("baseline");
        List<String> selectedCaseIds = selectValues("EVALUATION_CASES", CASE_IDS);
        List<String> candidateTypes = selectValues("EVALUATION_CANDIDATES", List.of("sample", "personal"));
        validateInputs(baselineRoot, selectedCaseIds, candidateTypes);

        String apiKey = resolveGeminiApiKey(evaluationRoot.getParent().resolve(".env"));
        String runId = "baseline-" + OffsetDateTime.now(SEOUL).format(RUN_ID_FORMAT);
        Path runRoot = evaluationRoot.resolve("runs").resolve(runId);
        Files.createDirectories(runRoot);

        List<Map<String, Object>> allMetrics = new ArrayList<>();
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(FeedbackmeApplication.class)
                .profiles("test", "legacy-gemini-baseline")
                .web(WebApplicationType.NONE)
                .run("--gemini.api-key=" + apiKey)) {
            CrawlingService crawlingService = context.getBean(CrawlingService.class);
            FileExtractService fileExtractService = context.getBean(FileExtractService.class);
            GeminiService geminiService = context.getBean(GeminiService.class);

            for (String caseId : selectedCaseIds) {
                Path caseRoot = baselineRoot.resolve(caseId);
                Path caseRunRoot = runRoot.resolve(caseId);
                Files.createDirectories(caseRunRoot);

                JobInput jobInput = readJobInput(caseRoot.resolve("job-posting.txt"));
                String jobUrl = jobInput.url();
                String jobDescription = jobInput.snapshot().isBlank()
                        ? crawlingService.crawlPosting(jobUrl).content()
                        : jobInput.snapshot();
                Files.writeString(caseRunRoot.resolve("job-posting.txt"), jobDescription, StandardCharsets.UTF_8);
                Files.writeString(caseRunRoot.resolve("job-url.txt"), jobUrl + System.lineSeparator(), StandardCharsets.UTF_8);

                if (candidateTypes.contains("sample")) {
                    allMetrics.add(runCandidate(
                            caseId, "sample", caseRoot.resolve("candidate.docx"), jobDescription,
                            caseRunRoot, fileExtractService, geminiService
                    ));
                }
                if (candidateTypes.contains("personal")) {
                    allMetrics.add(runCandidate(
                            caseId, "personal", caseRoot.resolve("candidate-personal.pdf"), jobDescription,
                            caseRunRoot, fileExtractService, geminiService
                    ));
                }
            }
        } finally {
            writeJson(runRoot.resolve("summary.json"), Map.of(
                    "runId", runId,
                    "model", "gemini-2.5-flash-lite",
                    "promptVersion", "legacy-inline",
                    "schemaVersion", "none",
                    "completedAt", OffsetDateTime.now(SEOUL).toString(),
                    "results", allMetrics
            ));
        }

        System.out.println("Baseline evaluation completed: " + runRoot);
    }

    private static Map<String, Object> runCandidate(
            String caseId,
            String candidateType,
            Path candidatePath,
            String jobDescription,
            Path caseRunRoot,
            FileExtractService fileExtractService,
            GeminiService geminiService
    ) {
        long startedAt = System.nanoTime();
        OffsetDateTime executedAt = OffsetDateTime.now(SEOUL);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("testCaseId", caseId);
        metrics.put("candidateType", candidateType);
        metrics.put("executedAt", executedAt.toString());
        metrics.put("inputFile", candidatePath.getFileName().toString());
        metrics.put("cacheUsed", false);

        try {
            byte[] bytes = Files.readAllBytes(candidatePath);
            MockMultipartFile file = new MockMultipartFile(
                    "file", candidatePath.getFileName().toString(), contentType(candidatePath), bytes
            );

            String result;
            if (isPdf(candidatePath)) {
                result = geminiService.getFeedBackWithVision(
                        jobDescription, fileExtractService.pdfToBase64Images(file)
                );
            } else {
                result = geminiService.getFeedBack(jobDescription, fileExtractService.extract(file));
            }

            Files.writeString(
                    caseRunRoot.resolve(candidateType + "-result.md"), result, StandardCharsets.UTF_8
            );
            metrics.put("status", "COMPLETED");
            metrics.put("resultCharacters", result.length());
            metrics.put("sectionMarkerCount", countSectionMarkers(result));
            metrics.put("formatCheckPassed", countSectionMarkers(result) == 8);
        } catch (Exception exception) {
            metrics.put("status", "FAILED");
            metrics.put("errorType", exception.getClass().getName());
            metrics.put("errorMessage", exception.getMessage() == null ? "" : exception.getMessage());
            try {
                Files.writeString(
                        caseRunRoot.resolve(candidateType + "-error.txt"),
                        exception.toString(), StandardCharsets.UTF_8
                );
            } catch (IOException ignored) {
                // The summary still contains the original failure.
            }
        }

        metrics.put("durationSeconds", Duration.ofNanos(System.nanoTime() - startedAt).toMillis() / 1000.0);
        try {
            writeJson(caseRunRoot.resolve(candidateType + "-metrics.json"), metrics);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save metrics for " + caseId + "/" + candidateType, exception);
        }
        return metrics;
    }

    private static void validateInputs(Path baselineRoot, List<String> caseIds, List<String> candidateTypes) {
        for (String caseId : caseIds) {
            Path caseRoot = baselineRoot.resolve(caseId);
            requireFile(caseRoot.resolve("job-posting.txt"));
            if (candidateTypes.contains("sample")) {
                requireFile(caseRoot.resolve("candidate.docx"));
            }
            if (candidateTypes.contains("personal")) {
                requireFile(caseRoot.resolve("candidate-personal.pdf"));
            }
        }
    }

    private static List<String> selectValues(String environmentName, List<String> allowedValues) {
        String configured = System.getenv(environmentName);
        if (configured == null || configured.isBlank()) {
            return allowedValues;
        }

        List<String> selected = List.of(configured.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        if (selected.isEmpty() || !allowedValues.containsAll(selected)) {
            throw new IllegalArgumentException(
                    environmentName + " contains unsupported values. Allowed: " + allowedValues
            );
        }
        return selected;
    }

    private static void requireFile(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Required evaluation input is missing: " + path);
        }
    }

    private static JobInput readJobInput(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        int urlLineIndex = -1;
        String url = "";
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (line.startsWith("URL:")) {
                urlLineIndex = index;
                url = line.substring("URL:".length()).trim();
                break;
            }
        }
        if (url.isBlank()) {
            throw new IllegalStateException("URL line is missing in " + path);
        }

        String snapshot = lines.subList(urlLineIndex + 1, lines.size()).stream()
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()))
                .trim();
        return new JobInput(url, snapshot);
    }

    private static String resolveGeminiApiKey(Path envPath) throws IOException {
        String fromEnvironment = System.getenv("GEMINI_API_KEY");
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment.trim();
        }

        if (Files.isRegularFile(envPath)) {
            return Files.readAllLines(envPath, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(line -> line.startsWith("GEMINI_API_KEY="))
                    .map(line -> line.substring("GEMINI_API_KEY=".length()).trim())
                    .map(BaselineEvaluationRunner::stripQuotes)
                    .filter(line -> !line.isBlank())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("GEMINI_API_KEY is missing in " + envPath));
        }

        throw new IllegalStateException("Set GEMINI_API_KEY or add it to " + envPath);
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean isPdf(Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(".pdf");
    }

    private static String contentType(Path path) {
        return isPdf(path)
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }

    private static int countSectionMarkers(String result) {
        int count = 0;
        for (int section = 1; section <= 8; section++) {
            if (result.contains("## " + section + ".")) {
                count++;
            }
        }
        return count;
    }

    private static void writeJson(Path path, Map<String, ?> value) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(
                temporary,
                new JSONObject(value).toString(2),
                StandardCharsets.UTF_8
        );
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private record JobInput(String url, String snapshot) {
    }
}
