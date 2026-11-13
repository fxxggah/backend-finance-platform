package com.finance.ai.poc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Avaliação manual de corpus sintético. Nunca roda no mvnw test. */
public final class GeminiVoiceEvaluation {
    static final String RESOURCE = "/ai-poc/evaluation-cases.json";
    static final String HOLDOUT_RESOURCE = "/ai-poc/evaluation-holdout.json";
    static final String UNSEEN_RESOURCE = "/ai-poc/evaluation-unseen.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private GeminiVoiceEvaluation() { }

    public static void main(String[] args) {
        if ((args.length != 3 && args.length != 4) || !"--free-project-confirmed".equals(args[0])
                || !"--synthetic-fixtures-only".equals(args[1])
                || args.length == 4 && !List.of("--holdout", "--unseen").contains(args[3])) {
            System.err.println("Uso: GeminiVoiceEvaluation --free-project-confirmed --synthetic-fixtures-only <diretorio-dos-wavs> [--holdout|--unseen]");
            System.exit(2);
            return;
        }
        try {
            evaluate(Path.of(args[2]), args.length == 4 ? args[3] : "development");
        } catch (Exception ex) {
            System.err.println("Avaliação interrompida (" + ex.getClass().getSimpleName()
                    + "). Confira corpus/manifesto/arquivos/ambiente; detalhes brutos omitidos.");
            System.exit(1);
        }
    }

    static Corpus corpus() throws Exception {
        return corpus(false);
    }

    static Corpus corpus(boolean holdout) throws Exception {
        return loadCorpus(holdout ? HOLDOUT_RESOURCE : RESOURCE, holdout ? "voice-holdout-v1" : "voice-corpus-v1", holdout ? 20 : 50, holdout ? "h" : "c");
    }

    static Corpus unseenCorpus() throws Exception {
        return loadCorpus(UNSEEN_RESOURCE, "voice-unseen-v1", 10, "u");
    }

    private static Corpus loadCorpus(String resource, String version, int count, String prefix) throws Exception {
        try (var input = GeminiVoiceEvaluation.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalArgumentException("Corpus ausente.");
            Corpus corpus = JSON.readValue(input, Corpus.class);
            if (!corpus.syntheticOnly() || corpus.cases().size() != count
                    || corpus.cases().stream().map(CaseSpec::id).distinct().count() != count
                    || !version.equals(corpus.version())) {
                throw new IllegalArgumentException("Corpus sintético inválido.");
            }
            LocalDate.parse(corpus.baseDate());
            for (CaseSpec sample : corpus.cases()) {
                if (!sample.id().matches(prefix + "[0-9]{2}") || sample.text() == null
                        || !List.of("clear", "partial", "multiple", "unclear", "injection").contains(sample.kind())) {
                    throw new IllegalArgumentException("Caso inválido.");
                }
                GeminiVoiceProbe.parse(sample.expected().toString());
            }
            return corpus;
        }
    }

    static Map<String, byte[]> verifyAudio(Corpus corpus, Path directory) throws Exception {
        Path root = directory.toRealPath();
        JsonNode manifest = JSON.readTree(Files.readString(root.resolve("synthetic-manifest.json")));
        byte[] resource;
        String resourcePath = switch (corpus.version()) {
            case "voice-corpus-v1" -> RESOURCE;
            case "voice-holdout-v1" -> HOLDOUT_RESOURCE;
            case "voice-unseen-v1" -> UNSEEN_RESOURCE;
            default -> throw new IllegalArgumentException("Corpus não permitido.");
        };
        try (var input = GeminiVoiceEvaluation.class.getResourceAsStream(resourcePath)) {
            resource = input.readAllBytes();
        }
        if (!manifest.path("syntheticOnly").asBoolean(false)
                || !manifest.path("corpusSha256").asText().equals(sha256(resource))
                || manifest.path("files").size() != corpus.cases().size()) {
            throw new IllegalArgumentException("Manifesto não corresponde ao corpus.");
        }
        Map<String, byte[]> audio = new LinkedHashMap<>();
        for (CaseSpec sample : corpus.cases()) {
            Path path = root.resolve(sample.id() + ".wav").toRealPath();
            if (!path.startsWith(root)) throw new IllegalArgumentException("Arquivo fora do diretório.");
            byte[] bytes = GeminiVoiceProbe.readWav(path);
            if (!sha256(bytes).equals(manifest.path("files").path(sample.id()).asText())) {
                throw new IllegalArgumentException("Hash de áudio diferente do manifesto.");
            }
            audio.put(sample.id(), bytes);
        }
        return audio;
    }

    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void evaluate(Path audioDirectory, String selector) throws Exception {
        Corpus corpus = "--unseen".equals(selector) ? unseenCorpus() : corpus("--holdout".equals(selector));
        Map<String, byte[]> audio = verifyAudio(corpus, audioDirectory);
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("GEMINI_API_KEY ausente.");
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        Clock clock = Clock.fixed(LocalDate.parse(corpus.baseDate()).atStartOfDay(zone).toInstant(), zone);
        Path output = Path.of("build", "ai-poc-evaluation", "results-" + Instant.now().toString().replaceAll("[:.]", "-"));
        Files.createDirectories(output);
        List<Result> results = new ArrayList<>();
        long lastStart = 0;
        int consecutiveFailures = 0;
        try (Client client = Client.builder().apiKey(apiKey).vertexAI(false)
                .httpOptions(GeminiVoiceProbe.httpOptions()).build()) {
            for (CaseSpec sample : corpus.cases()) {
                if (lastStart != 0) {
                    long remaining = 6_000_000_000L - (System.nanoTime() - lastStart);
                    if (remaining > 0) Thread.sleep(java.time.Duration.ofNanos(remaining));
                }
                lastStart = System.nanoTime();
                Result result = request(client, sample, audio.get(sample.id()), clock);
                results.add(result);
                Files.writeString(output.resolve("results.jsonl"), JSON.writeValueAsString(result) + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                Files.writeString(output.resolve("summary.json"), JSON.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(summary(corpus, results)));
                System.out.println("caso=" + sample.id() + " grupo=" + sample.kind() + " resultado=" + result.outcome()
                        + " divergencias=" + result.mismatches() + " latenciaMs=" + result.latencyMs()
                        + " http=" + result.httpStatus());
                consecutiveFailures = "MATCH".equals(result.outcome()) || "MISMATCH".equals(result.outcome())
                        ? 0 : consecutiveFailures + 1;
                if (shouldStop(result, consecutiveFailures)) {
                    System.out.println("Interrompido por erro de acesso/cota/configuração ou falhas consecutivas; sem retries.");
                    break;
                }
            }
        }
        System.out.println("Resumo: " + output.resolve("summary.json"));
        System.out.println(JSON.writeValueAsString(summary(corpus, results)));
    }

    static boolean shouldStop(Result result, int consecutiveFailures) {
        return List.of(400, 401, 403, 404, 429).contains(result.httpStatus()) || consecutiveFailures >= 5;
    }

    static Result request(Client client, CaseSpec sample, byte[] bytes, Clock clock) {
        long start = System.nanoTime();
        try {
            GenerateContentResponse response = client.models.generateContent(GeminiVoiceProbe.MODEL,
                    Content.fromParts(Part.fromBytes(bytes, "audio/wav")), GeminiVoiceProbe.config(clock));
            long elapsed = elapsed(start);
            var usage = response.usageMetadata();
            Integer input = usage.flatMap(u -> u.promptTokenCount()).orElse(null);
            Integer output = usage.flatMap(u -> u.candidatesTokenCount()).orElse(null);
            Integer thoughts = usage.flatMap(u -> u.thoughtsTokenCount()).orElse(null);
            Integer total = usage.flatMap(u -> u.totalTokenCount()).orElse(null);
            if (response.finishReason() == null || !"STOP".equals(response.finishReason().toString())
                    || !response.functionCalls().isEmpty()) {
                return new Result(sample.id(), sample.kind(), "INCOMPLETE_RESULT", elapsed, 200,
                        GeminiVoiceProbe.FIELDS.stream().sorted().toList(), input, output, thoughts, total);
            }
            Comparison comparison = compare(sample.expected(), response.text());
            String outcome = !comparison.validated() ? "INVALID_RESULT" : comparison.mismatches().isEmpty() ? "MATCH" : "MISMATCH";
            return new Result(sample.id(), sample.kind(), outcome, elapsed, 200, comparison.mismatches(), input, output, thoughts, total);
        } catch (ApiException ex) {
            return error(sample, "HTTP_ERROR", start, ex.code());
        } catch (Exception ex) {
            return error(sample, "REQUEST_ERROR", start, 0);
        }
    }

    private static long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }

    private static Result error(CaseSpec sample, String outcome, long start, int http) {
        return new Result(sample.id(), sample.kind(), outcome, elapsed(start), http,
                GeminiVoiceProbe.FIELDS.stream().sorted().toList(), null, null, null, null);
    }

    static Comparison compare(JsonNode expected, String response) {
        try {
            JsonNode currentExpected = expectedForCurrentPolicy(expected);
            var draft = GeminiVoiceProbe.parseExtraction(response);
            ObjectNode actual = JSON.createObjectNode();
            actual.put("status", draft.status()).put("type", draft.type())
                    .put("amount", draft.amount() == null ? null : draft.amount().toPlainString())
                    .put("date", draft.date() == null ? null : draft.date().toString())
                    .put("installments", draft.installments()).put("categoryRef", draft.categoryRef())
                    .put("accountRef", draft.accountRef()).put("creditCardRef", draft.creditCardRef());
            return new Comparison(true, GeminiVoiceProbe.FIELDS.stream().sorted()
                    .filter(field -> !currentExpected.get(field).equals(actual.get(field))).toList());
        } catch (Exception ex) {
            return new Comparison(false, GeminiVoiceProbe.FIELDS.stream().sorted().toList());
        }
    }

    /** Política aprovada por Gabriel; nunca altera o corpus histórico em memória ou disco. */
    static JsonNode expectedForCurrentPolicy(JsonNode historical) {
        ObjectNode expected = ((ObjectNode) historical).deepCopy();
        if ("SINGLE".equals(expected.path("status").asText()) && expected.path("accountRef").isTextual()
                && expected.path("creditCardRef").isNull() && expected.path("installments").isNull()) {
            expected.put("installments", 1);
        }
        return expected;
    }

    static Map<String, Object> summary(Corpus corpus, List<Result> results) {
        long validated = results.stream().filter(r -> List.of("MATCH", "MISMATCH").contains(r.outcome())).count();
        long matches = results.stream().filter(r -> "MATCH".equals(r.outcome())).count();
        long clearPlanned = corpus.cases().stream().filter(c -> "clear".equals(c.kind())).count();
        long clearCriticalCorrect = results.stream().filter(r -> "clear".equals(r.kind()))
                .filter(r -> List.of("MATCH", "MISMATCH").contains(r.outcome()))
                .filter(r -> r.mismatches().stream().noneMatch(List.of("amount", "type", "installments")::contains)).count();
        Map<String, Long> fieldMatches = new LinkedHashMap<>();
        GeminiVoiceProbe.FIELDS.stream().sorted().forEach(field -> fieldMatches.put(field,
                results.stream().filter(r -> List.of("MATCH", "MISMATCH").contains(r.outcome()))
                        .filter(r -> !r.mismatches().contains(field)).count()));
        Map<String, Object> groups = new LinkedHashMap<>();
        corpus.cases().stream().map(CaseSpec::kind).distinct().forEach(kind -> groups.put(kind, Map.of(
                "planned", corpus.cases().stream().filter(c -> kind.equals(c.kind())).count(),
                "executed", results.stream().filter(r -> kind.equals(r.kind())).count(),
                "exactMatches", results.stream().filter(r -> kind.equals(r.kind()) && "MATCH".equals(r.outcome())).count())));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("model", GeminiVoiceProbe.MODEL);
        report.put("prompt", GeminiVoiceProbe.PROMPT_VERSION);
        report.put("normalization", GeminiVoiceProbe.NORMALIZATION_VERSION);
        report.put("effectiveOracleChanges", corpus.cases().stream()
                .filter(c -> !c.expected().equals(expectedForCurrentPolicy(c.expected()))).count());
        report.put("corpus", corpus.version());
        report.put("contextDate", corpus.baseDate());
        report.put("planned", corpus.cases().size());
        report.put("executed", results.size());
        report.put("notExecuted", corpus.cases().size() - results.size());
        report.put("validatedResponses", validated);
        report.put("invalidOrFailed", results.size() - validated);
        report.put("exactMatches", matches);
        report.put("fieldMatchesOutOfPlanned", fieldMatches);
        report.put("clearCriticalCorrect", clearCriticalCorrect);
        report.put("clearPlanned", clearPlanned);
        report.put("byKind", groups);
        report.put("latencyP50Ms", percentile(results.stream().map(Result::latencyMs).toList(), 50));
        report.put("latencyP95Ms", percentile(results.stream().map(Result::latencyMs).toList(), 95));
        report.put("reportedInputTokens", results.stream().filter(r -> r.inputTokens() != null).mapToLong(Result::inputTokens).sum());
        report.put("reportedOutputTokens", results.stream().filter(r -> r.outputTokens() != null).mapToLong(Result::outputTokens).sum());
        report.put("reportedTotalTokens", results.stream().filter(r -> r.totalTokens() != null).mapToLong(Result::totalTokens).sum());
        report.put("requestsWithoutUsage", results.stream().filter(r -> r.totalTokens() == null).count());
        report.put("note", "Corpus sintético de desenvolvimento; erros não são acertos, pendências não são aprovação, uso ausente não é zero. Latências incluem respostas HTTP de erro, sem espera do limitador.");
        return report;
    }

    static long percentile(List<Long> values, int percent) {
        if (values.isEmpty()) return -1;
        if (percent < 1 || percent > 100) throw new IllegalArgumentException("Percentil inválido.");
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get((int) Math.ceil(sorted.size() * percent / 100.0) - 1);
    }

    record Corpus(String version, String baseDate, boolean syntheticOnly, List<CaseSpec> cases) { }
    record CaseSpec(String id, String kind, String text, JsonNode expected) { }
    record Comparison(boolean validated, List<String> mismatches) { }
    record Result(String id, String kind, String outcome, long latencyMs, int httpStatus, List<String> mismatches,
                  Integer inputTokens, Integer outputTokens, Integer thinkingTokens, Integer totalTokens) { }
}
