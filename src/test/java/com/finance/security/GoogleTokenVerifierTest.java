package com.finance.security;

import com.finance.common.exception.BusinessRuleException;
import com.google.api.client.json.gson.GsonFactory;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleTokenVerifierTest {
    private static final String CLIENT = "test-client.apps.googleusercontent.com";
    private static final String ISSUER = "https://accounts.google.com";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);
    private static final GsonFactory JSON = GsonFactory.getDefaultInstance();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static PrivateKey signingKey;
    private static byte[] certificates;

    @BeforeAll
    static void createSigningFixture(@TempDir Path directory) throws Exception {
        Path keyStoreFile = directory.resolve("test.p12");
        String fixturePassword = "only-for-this-test";
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "fixture", "-keyalg", "RSA", "-keysize", "2048", "-validity", "365",
                "-dname", "CN=FIN test", "-storetype", "PKCS12", "-keystore", keyStoreFile.toString(),
                "-storepass", fixturePassword, "-noprompt").redirectErrorStream(true).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Tempo excedido ao gerar o certificado de teste.");
        }
        assertThat(process.exitValue()).isZero();
        var store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keyStoreFile)) {
            store.load(input, fixturePassword.toCharArray());
        }
        signingKey = (PrivateKey) store.getKey("fixture", fixturePassword.toCharArray());
        var certificate = store.getCertificate("fixture");
        String pem = "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder().encodeToString(certificate.getEncoded()) + "\n-----END CERTIFICATE-----";
        certificates = JSON.toByteArray(Map.of("fixture", pem));
    }

    @Test
    void validatesSignatureAndClaimsUsingCachedCertificates() throws Exception {
        try (var server = new CertificateServer(200, 0)) {
            var verifier = server.verifier(2_000);
            var claims = verifier.verify(token(Map.of(), true));
            assertThat(claims).isEqualTo(new GoogleTokenVerifier.GoogleClaims(
                    "google-user", "fin@example.test", "FIN Teste", "https://example.test/photo"));
            assertThat(verifier.verify(token(Map.of("name", "", "iss", "accounts.google.com"), true)).name())
                    .isEqualTo("fin@example.test");
            assertThatThrownBy(() -> verifier.verify(token(Map.of(), false)))
                    .isInstanceOf(BusinessRuleException.class);
            assertThat(server.calls.get()).isEqualTo(1);
        }
    }

    @Test
    void rejectsInvalidClaimsWithoutFetchingCertificates() throws Exception {
        long now = CLOCK.instant().getEpochSecond();
        try (var server = new CertificateServer(200, 0)) {
            var verifier = server.verifier(2_000);
            for (Map<String, ?> override : List.of(
                    Map.of("aud", "wrong-client"), Map.of("aud", 123), Map.of("aud", List.of(123)),
                    Map.of("iss", "https://example.test"),
                    Map.of("exp", now), Map.of("iat", now + 301),
                    Map.of("email_verified", false), Map.of("email_verified", 123),
                    Map.of("email", ""), Map.of("sub", ""))) {
                assertThatThrownBy(() -> verifier.verify(token(override, true)))
                        .isInstanceOf(BusinessRuleException.class).as("claim %s", override.keySet());
            }
            assertThatThrownBy(() -> verifier.verify("not-a-jwt")).isInstanceOf(BusinessRuleException.class);
            assertThat(server.calls.get()).isZero();
        }
    }

    @Test
    void distinguishesUnavailableCertificatesFromAnInvalidTokenAndHidesUpstreamDetails() throws Exception {
        for (int status : new int[]{503, 200}) {
            try (var server = new CertificateServer(status, status == 200 ? 500 : 0)) {
                var verifier = server.verifier(100);
                assertThatThrownBy(() -> verifier.verify(token(Map.of(), true)))
                        .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                            assertThat(error.getMessage()).doesNotContain("private upstream detail");
                            assertThat(error.getCause()).isNull();
                        });
                assertThat(server.calls.get()).isEqualTo(1);
            }
        }
    }

    private static String token(Map<String, ?> overrides, boolean validSignature) throws Exception {
        long now = CLOCK.instant().getEpochSecond();
        var payload = new HashMap<String, Object>(Map.of(
                "iss", ISSUER, "aud", CLIENT, "exp", now + 300, "iat", now,
                "sub", "google-user", "email", "fin@example.test", "email_verified", true,
                "name", "FIN Teste", "picture", "https://example.test/photo"));
        payload.putAll(overrides);
        String compact = BASE64_URL.encodeToString(JSON.toByteArray(Map.of("alg", "RS256", "kid", "fixture")))
                + "." + BASE64_URL.encodeToString(JSON.toByteArray(payload));
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(signingKey);
        signature.update(compact.getBytes(StandardCharsets.US_ASCII));
        byte[] signed = signature.sign();
        if (!validSignature) signed[signed.length - 1] ^= 1;
        return compact + "." + BASE64_URL.encodeToString(signed);
    }

    private static final class CertificateServer implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger calls = new AtomicInteger();

        CertificateServer(int status, int delayMillis) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/certs", exchange -> {
                calls.incrementAndGet();
                try (exchange) {
                    byte[] body = status == 200 ? certificates : "private upstream detail".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(status, body.length);
                    if (delayMillis > 0) Thread.sleep(delayMillis);
                    exchange.getResponseBody().write(body);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            server.start();
        }

        GoogleTokenVerifier verifier(int timeout) {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/certs");
            return new GoogleTokenVerifier(CLIENT, CLOCK, endpoint, timeout, timeout);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
