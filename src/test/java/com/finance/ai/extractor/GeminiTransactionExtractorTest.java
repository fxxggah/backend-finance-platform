package com.finance.ai.extractor;

import com.finance.ai.exception.AiException;
import com.finance.ai.model.AudioInput;
import com.finance.ai.model.ExtractionCatalog;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class GeminiTransactionExtractorTest {
    private static final AudioInput AUDIO = new AudioInput(new byte[]{1}, "audio/wav", Duration.ofSeconds(1));
    private static final ExtractionCatalog CATALOG = new ExtractionCatalog(Map.of(), Map.of(), Map.of());

    private Client client(int port, int timeout) {
        return Client.builder().apiKey("synthetic-test-key").vertexAI(false).httpOptions(HttpOptions.builder()
                .baseUrl("http://127.0.0.1:" + port).apiVersion("v1beta").timeout(timeout)
                .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build();
    }

    @ParameterizedTest @ValueSource(ints = {429, 503, 403})
    void convertsProviderErrorsWithoutRetryOrLeakingBody(int status) throws Exception {
        var count = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            count.incrementAndGet();
            byte[] body = ("{\"error\":{\"code\":" + status + ",\"message\":\"private-fixture-content\",\"status\":\"UNAVAILABLE\"}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var extractor = new GeminiTransactionExtractor("synthetic-test-key", () -> client(server.getAddress().getPort(), 5000));
            assertThatThrownBy(() -> extractor.extract(AUDIO, CATALOG, LocalDate.of(2026, 11, 14)))
                    .isInstanceOf(AiException.class).hasMessageNotContaining("private-fixture-content").hasNoCause()
                    .extracting("code").isEqualTo(status == 429 ? "AI_RATE_LIMITED" : "AI_UNAVAILABLE");
            assertThat(count).hasValue(1);
        } finally { server.stop(0); }
    }

    @Test void timeoutRemainsControlledWithoutRetry() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var workers = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(workers);
        var count = new AtomicInteger();
        server.createContext("/", exchange -> {
            count.incrementAndGet();
            try { Thread.sleep(2000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var extractor = new GeminiTransactionExtractor("synthetic-test-key", () -> client(server.getAddress().getPort(), 100));
            assertThatThrownBy(() -> extractor.extract(AUDIO, CATALOG, LocalDate.of(2026, 11, 14)))
                    .isInstanceOf(AiException.class).extracting("code").isEqualTo("AI_UNAVAILABLE");
            assertThat(count.get()).isLessThanOrEqualTo(1);
        } finally { server.stop(0); workers.shutdownNow(); }
    }

    @Test void missingKeyDoesNotInstantiateClient() {
        var count = new AtomicInteger();
        var extractor = new GeminiTransactionExtractor("", () -> { count.incrementAndGet(); throw new AssertionError(); });
        assertThatThrownBy(() -> extractor.extract(AUDIO, CATALOG, LocalDate.of(2026, 11, 14)))
                .isInstanceOf(AiException.class).extracting("code").isEqualTo("AI_UNAVAILABLE");
        assertThat(count).hasValue(0);
    }

    @Test void acceptsStructuredResponseFromLocalServerAndRejectsIncompleteFinish() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var finish = new java.util.concurrent.atomic.AtomicReference<>("STOP");
        server.createContext("/", exchange -> {
            byte[] body = ("{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"{\\\"events\\\":[]}\"}]},"
                    + "\"finishReason\":\"" + finish.get() + "\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var extractor = new GeminiTransactionExtractor("synthetic-test-key", () -> client(server.getAddress().getPort(), 5000));
            assertThat(extractor.extract(AUDIO, CATALOG, LocalDate.of(2026, 11, 14))).isEqualTo("{\"events\":[]}");
            finish.set("MAX_TOKENS");
            assertThatThrownBy(() -> extractor.extract(AUDIO, CATALOG, LocalDate.of(2026, 11, 14)))
                    .isInstanceOf(AiException.class).extracting("code").isEqualTo("INVALID_AI_RESPONSE");
        } finally { server.stop(0); }
    }
}
