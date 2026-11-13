package com.finance.ai.poc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiVoiceEvaluationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporary;

    @Test
    void corpusHasFiftyDistinctValidOraclesAndRequiredGroups() throws Exception {
        var corpus = GeminiVoiceEvaluation.corpus();
        assertThat(corpus.cases()).hasSize(50);
        assertThat(corpus.cases().stream().filter(c -> "clear".equals(c.kind())).count()).isEqualTo(30);
        assertThat(corpus.cases().stream().map(GeminiVoiceEvaluation.CaseSpec::kind).distinct())
                .containsExactlyInAnyOrder("clear", "partial", "multiple", "unclear", "injection");
        assertThat(corpus.cases().stream().map(GeminiVoiceEvaluation.CaseSpec::id)).doesNotHaveDuplicates();
    }

    @Test
    void reservedCorpusHasDistinctCasesAndSinglePurchaseCounterexamples() throws Exception {
        var reserved = GeminiVoiceEvaluation.corpus(true);
        assertThat(reserved.cases()).hasSize(20);
        assertThat(reserved.cases().stream().filter(c -> "multiple".equals(c.kind())).count()).isEqualTo(8);
        assertThat(reserved.cases().stream().filter(c -> "clear".equals(c.kind())).count()).isEqualTo(6);
        var oldTexts = GeminiVoiceEvaluation.corpus().cases().stream().map(GeminiVoiceEvaluation.CaseSpec::text).toList();
        assertThat(reserved.cases().stream().map(GeminiVoiceEvaluation.CaseSpec::text)).doesNotContainAnyElementsOf(oldTexts);
    }

    @Test
    void finalUnseenCorpusDoesNotReusePreviouslyInspectedPhrases() throws Exception {
        var unseen = GeminiVoiceEvaluation.unseenCorpus();
        assertThat(unseen.cases()).hasSize(10);
        var prior = java.util.stream.Stream.concat(GeminiVoiceEvaluation.corpus().cases().stream(),
                GeminiVoiceEvaluation.corpus(true).cases().stream()).map(GeminiVoiceEvaluation.CaseSpec::text).toList();
        assertThat(unseen.cases().stream().map(GeminiVoiceEvaluation.CaseSpec::text)).doesNotContainAnyElementsOf(prior);
    }

    @Test
    void exactMatchAndValueErrorAreScoredPerField() throws Exception {
        var expected = GeminiVoiceEvaluation.corpus().cases().getFirst().expected();
        assertThat(GeminiVoiceEvaluation.compare(expected, wire(expected)).mismatches()).isEmpty();
        var mismatch = GeminiVoiceEvaluation.compare(expected, wire(expected).replace("45.00", "46.00"));
        assertThat(mismatch.validated()).isTrue();
        assertThat(mismatch.mismatches()).containsExactly("amount");
    }

    @Test
    void fabricatedMissingFieldIsNotScoredAsCorrect() throws Exception {
        var expected = GeminiVoiceEvaluation.corpus().cases().get(30).expected();
        var actual = expected.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) actual).put("amount", "10.00");
        assertThat(GeminiVoiceEvaluation.compare(expected, wire(actual)).mismatches()).containsExactly("amount");
    }

    @Test
    void malformedOrForeignReferenceCannotEarnPartialCredit() throws Exception {
        var expected = GeminiVoiceEvaluation.corpus().cases().getFirst().expected();
        for (String invalid : List.of("not json", wire(expected).replace("acc_wallet", "foreign_account"))) {
            var result = GeminiVoiceEvaluation.compare(expected, invalid);
            assertThat(result.validated()).isFalse();
            assertThat(result.mismatches()).containsExactlyElementsOf(GeminiVoiceProbe.FIELDS.stream().sorted().toList());
        }
    }

    @Test
    void summaryDoesNotHideHttpErrorsOrUnexecutedCases() throws Exception {
        var corpus = GeminiVoiceEvaluation.corpus();
        var results = List.of(
                new GeminiVoiceEvaluation.Result("c01", "clear", "MATCH", 100, 200, List.of(), 10, 5, null, 15),
                new GeminiVoiceEvaluation.Result("c02", "clear", "HTTP_ERROR", 200, 503,
                        GeminiVoiceProbe.FIELDS.stream().sorted().toList(), null, null, null, null));
        var report = GeminiVoiceEvaluation.summary(corpus, results);
        assertThat(report.get("planned")).isEqualTo(50);
        assertThat(report.get("executed")).isEqualTo(2);
        assertThat(report.get("notExecuted")).isEqualTo(48);
        assertThat(report.get("validatedResponses")).isEqualTo(1L);
        assertThat(report.get("invalidOrFailed")).isEqualTo(1L);
        assertThat(report.get("exactMatches")).isEqualTo(1L);
        assertThat(report.get("clearCriticalCorrect")).isEqualTo(1L);
        assertThat(report.get("clearPlanned")).isEqualTo(30L);
        assertThat(report.get("requestsWithoutUsage")).isEqualTo(1L);
        assertThat(report.get("reportedTotalTokens")).isEqualTo(15L);
        assertThat(((Map<?, ?>) report.get("fieldMatchesOutOfPlanned")).get("amount")).isEqualTo(1L);
    }

    @Test
    void nearestRankPercentilesHandleSmallSamplesAndNoData() {
        assertThat(GeminiVoiceEvaluation.percentile(List.of(300L, 100L, 200L), 50)).isEqualTo(200);
        assertThat(GeminiVoiceEvaluation.percentile(List.of(300L, 100L, 200L), 95)).isEqualTo(300);
        assertThat(GeminiVoiceEvaluation.percentile(List.of(), 95)).isEqualTo(-1);
        assertThatThrownBy(() -> GeminiVoiceEvaluation.percentile(List.of(1L), 101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesAllFixtureHashesBeforeAnyRequestAndRejectsTampering() throws Exception {
        var corpus = GeminiVoiceEvaluation.corpus();
        byte[] wave;
        var format = new AudioFormat(16_000, 16, 1, true, false);
        var bytes = new java.io.ByteArrayOutputStream();
        try (var stream = new AudioInputStream(new ByteArrayInputStream(new byte[32_000]), format, 16_000)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, bytes);
            wave = bytes.toByteArray();
        }
        var hashes = JSON.createObjectNode();
        for (var sample : corpus.cases()) {
            Files.write(temporary.resolve(sample.id() + ".wav"), wave);
            hashes.put(sample.id(), GeminiVoiceEvaluation.sha256(wave));
        }
        byte[] resource;
        try (var input = getClass().getResourceAsStream(GeminiVoiceEvaluation.RESOURCE)) { resource = input.readAllBytes(); }
        var manifest = JSON.createObjectNode().put("syntheticOnly", true)
                .put("corpusSha256", GeminiVoiceEvaluation.sha256(resource));
        manifest.set("files", hashes);
        Files.writeString(temporary.resolve("synthetic-manifest.json"), manifest.toString());
        assertThat(GeminiVoiceEvaluation.verifyAudio(corpus, temporary)).hasSize(50);
        wave[wave.length - 1] = 1;
        Files.write(temporary.resolve("c01.wav"), wave);
        assertThatThrownBy(() -> GeminiVoiceEvaluation.verifyAudio(corpus, temporary)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsManifestForAnotherCorpusBeforeReadingAudio() throws Exception {
        Files.writeString(temporary.resolve("synthetic-manifest.json"), "{\"syntheticOnly\":true,\"corpusSha256\":\"wrong\",\"files\":{}}");
        assertThatThrownBy(() -> GeminiVoiceEvaluation.verifyAudio(GeminiVoiceEvaluation.corpus(), temporary))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quotaErrorDoesNotRetryOrExposeResponseBody() throws Exception {
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = "{\"error\":{\"code\":429,\"message\":\"synthetic quota fixture\",\"status\":\"RESOURCE_EXHAUSTED\"}}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try (var client = localClient(server.getAddress().getPort(), 2_000)) {
            var result = GeminiVoiceEvaluation.request(client, GeminiVoiceEvaluation.corpus().cases().getFirst(),
                    new byte[44], java.time.Clock.systemUTC());
            assertThat(result.outcome()).isEqualTo("HTTP_ERROR");
            assertThat(result.httpStatus()).isEqualTo(429);
            assertThat(result.totalTokens()).isNull();
            assertThat(requests.get()).isEqualTo(1);
            assertThat(GeminiVoiceEvaluation.shouldStop(result, 1)).isTrue();
            assertThat(JSON.writeValueAsString(result)).doesNotContain("synthetic quota fixture");
        } finally { server.stop(0); }
    }

    @Test
    void localTimeoutIsRecordedWithoutRetry() throws Exception {
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            try { Thread.sleep(1_000); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try (var client = localClient(server.getAddress().getPort(), 200)) {
            var result = GeminiVoiceEvaluation.request(client, GeminiVoiceEvaluation.corpus().cases().getFirst(),
                    new byte[44], java.time.Clock.systemUTC());
            assertThat(result.outcome()).isIn("REQUEST_ERROR", "HTTP_ERROR");
            assertThat(result.totalTokens()).isNull();
            assertThat(requests.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test
    void transientFailureOnlyStopsAfterFiveConsecutiveFailures() {
        var result = new GeminiVoiceEvaluation.Result("c01", "clear", "HTTP_ERROR", 10, 503,
                List.of(), null, null, null, null);
        assertThat(GeminiVoiceEvaluation.shouldStop(result, 4)).isFalse();
        assertThat(GeminiVoiceEvaluation.shouldStop(result, 5)).isTrue();
    }

    private com.google.genai.Client localClient(int port, int timeout) {
        // Credencial fictícia e destino estritamente loopback: nunca chama o Google.
        return com.google.genai.Client.builder().apiKey("poc-local-test-not-a-secret").vertexAI(false)
                .httpOptions(com.google.genai.types.HttpOptions.builder().baseUrl("http://127.0.0.1:" + port)
                        .timeout(timeout).retryOptions(com.google.genai.types.HttpRetryOptions.builder().attempts(1).build()).build())
                .build();
    }

    private String wire(com.fasterxml.jackson.databind.JsonNode expected) {
        var event = ((com.fasterxml.jackson.databind.node.ObjectNode) expected).deepCopy();
        event.remove("status");
        var root = JSON.createObjectNode();
        root.putArray("events").add(event);
        return root.toString();
    }

    @Test
    void missingInstallmentsOnValidatedAccountAreNormalizedToOne() throws Exception {
        var historical = GeminiVoiceEvaluation.corpus().cases().get(34).expected();
        var draft = GeminiVoiceProbe.parseExtraction(wire(historical));
        assertThat(draft.installments()).isEqualTo(1);
        assertThat(draft.missingFields()).doesNotContain("installments");
        assertThat(draft.amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void missingInstallmentsOnCardAndUnknownSourceStayPending() throws Exception {
        var historical = (com.fasterxml.jackson.databind.node.ObjectNode) GeminiVoiceEvaluation.corpus().cases().get(34).expected().deepCopy();
        historical.putNull("accountRef");
        for (String card : new String[]{null, "card_nubank"}) {
            historical.put("creditCardRef", card);
            var draft = GeminiVoiceProbe.parseExtraction(wire(historical));
            assertThat(draft.installments()).isNull();
            assertThat(draft.missingFields()).contains("installments");
        }
        historical.put("installments", 2);
        assertThat(GeminiVoiceProbe.parseExtraction(wire(historical)).installments()).isEqualTo(2);
    }

    @Test
    void defaultDoesNotMaskExplicitInvalidAccountInstallmentsOrXor() throws Exception {
        var value = (com.fasterxml.jackson.databind.node.ObjectNode) GeminiVoiceEvaluation.corpus().cases().get(34).expected().deepCopy();
        value.put("installments", 2);
        assertThatThrownBy(() -> GeminiVoiceProbe.parseExtraction(wire(value))).isInstanceOf(IllegalArgumentException.class);
        value.putNull("installments");
        value.put("creditCardRef", "card_nubank");
        assertThatThrownBy(() -> GeminiVoiceProbe.parseExtraction(wire(value))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void effectiveOracleChangesOnlyApprovedCaseWithoutMutatingHistory() throws Exception {
        var corpus = GeminiVoiceEvaluation.corpus();
        var historical = corpus.cases().get(34).expected();
        assertThat(historical.path("installments").isNull()).isTrue();
        assertThat(GeminiVoiceEvaluation.expectedForCurrentPolicy(historical).path("installments").intValue()).isEqualTo(1);
        assertThat(GeminiVoiceEvaluation.compare(historical, wire(historical)).mismatches()).isEmpty();
        assertThat(historical.path("installments").isNull()).isTrue();
        assertThat(corpus.cases().stream().filter(c -> !c.expected().equals(GeminiVoiceEvaluation.expectedForCurrentPolicy(c.expected())))
                .map(GeminiVoiceEvaluation.CaseSpec::id)).containsExactly("c35");
    }

    @Test
    void reportIdentifiesPolicyAndDoesNotRescoreOldResultsAsNewMatches() throws Exception {
        var report = GeminiVoiceEvaluation.summary(GeminiVoiceEvaluation.corpus(), List.of());
        assertThat(report.get("normalization")).isEqualTo("account-installments-v1");
        assertThat(report.get("effectiveOracleChanges")).isEqualTo(1L);
        assertThat(report.get("exactMatches")).isEqualTo(0L);
        assertThat(report.get("notExecuted")).isEqualTo(50);
        var multiple = GeminiVoiceProbe.parseExtraction("{\"events\":[]}");
        assertThat(multiple.installments()).isNull();
    }

    @Test
    void backendRejectsMultipleEventsWithoutUsingTheirValues() throws Exception {
        var expected = GeminiVoiceEvaluation.corpus().cases().getFirst().expected();
        var one = JSON.readTree(wire(expected));
        ((com.fasterxml.jackson.databind.node.ArrayNode) one.get("events")).add(one.get("events").get(0).deepCopy());
        var draft = GeminiVoiceProbe.parseExtraction(one.toString());
        assertThat(draft.status()).isEqualTo("MULTIPLE");
        assertThat(draft.amount()).isNull();
        assertThat(draft.accountRef()).isNull();
        assertThat(draft.type()).isNull();
    }

    @Test
    void emptyEventsMeanUnclearAndMalformedEnvelopeIsRejected() throws Exception {
        assertThat(GeminiVoiceProbe.parseExtraction("{\"events\":[]}").status()).isEqualTo("UNCLEAR");
        for (String malformed : List.of("{\"events\":null}", "{\"events\":[{}]}",
                "{\"events\":[],\"status\":\"SINGLE\"}", "{\"events\":[],\"events\":[]}")) {
            assertThatThrownBy(() -> GeminiVoiceProbe.parseExtraction(malformed)).isInstanceOf(Exception.class);
        }
    }

    @Test
    void extractionDoesNotTrustModelStatusAndRejectsTooManyEvents() throws Exception {
        var expected = GeminiVoiceEvaluation.corpus().cases().getFirst().expected();
        var one = JSON.readTree(wire(expected));
        var events = (com.fasterxml.jackson.databind.node.ArrayNode) one.get("events");
        events.add(events.get(0).deepCopy());
        events.add(events.get(0).deepCopy());
        assertThat(GeminiVoiceProbe.parseExtraction(one.toString()).status()).isEqualTo("MULTIPLE");
        events.add(events.get(0).deepCopy());
        assertThatThrownBy(() -> GeminiVoiceProbe.parseExtraction(one.toString())).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> GeminiVoiceProbe.parseExtraction(expected.toString())).isInstanceOf(Exception.class);
    }
}
