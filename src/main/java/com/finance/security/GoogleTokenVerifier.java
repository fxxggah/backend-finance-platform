package com.finance.security;

import com.finance.common.exception.BusinessRuleException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.SignatureException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Valida a credencial localmente; a rede é usada apenas para obter certificados públicos. */
@Component
public class GoogleTokenVerifier {
    private static final URI CERTIFICATES = URI.create("https://www.googleapis.com/oauth2/v1/certs");
    private static final GsonFactory JSON = GsonFactory.getDefaultInstance();

    private final Clock clock;
    private final GoogleIdTokenVerifier verifier;

    @Autowired
    public GoogleTokenVerifier(@Value("${app.google.client-id}") String clientId, Clock clock) {
        this(clientId, clock, CERTIFICATES, 2_000, 3_000);
    }

    GoogleTokenVerifier(String clientId, Clock clock, URI certificates, int connectTimeout, int readTimeout) {
        if (clientId.isBlank()) throw new IllegalArgumentException("Configure o client ID do Google.");
        this.clock = clock;
        var keys = new GooglePublicKeysManager.Builder(
                new CertificateTransport(certificates, connectTimeout, readTimeout), JSON)
                .setPublicCertsEncodedUrl(certificates.toString())
                .setClock(clock::millis)
                .build();
        this.verifier = new GoogleIdTokenVerifier.Builder(keys)
                .setAudience(List.of(clientId))
                .setClock(clock::millis)
                .build();
    }

    public GoogleClaims verify(String idToken) {
        GoogleIdToken token;
        try {
            token = GoogleIdToken.parse(JSON, idToken);
        } catch (IOException | RuntimeException invalid) {
            throw rejected();
        }
        var payload = token.getPayload();
        Long expires = payload.getExpirationTimeSeconds();
        Object audience = payload.getAudience();
        Object emailVerified = payload.get("email_verified");
        if (!"RS256".equals(token.getHeader().getAlgorithm()) || expires == null
                || expires <= clock.instant().getEpochSecond() || payload.getIssuedAtTimeSeconds() == null
                || empty(payload.getSubject()) || empty(payload.getEmail())
                || !wellFormedAudience(audience)
                || (!Boolean.TRUE.equals(emailVerified) && !"true".equals(emailVerified))) {
            throw rejected();
        }
        boolean valid;
        try {
            valid = verifier.verify(token);
        } catch (SignatureException invalid) {
            throw rejected();
        } catch (GeneralSecurityException | IOException | IllegalArgumentException invalidCertificates) {
            // A mensagem do SDK pode conter o corpo da resposta. Não propagar a causa.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível validar a credencial do Google agora. Tente novamente.");
        }
        if (!valid) throw rejected();
        Object nameClaim = payload.get("name");
        String name = nameClaim instanceof String value && !value.isBlank() ? value : payload.getEmail();
        Object picture = payload.get("picture");
        return new GoogleClaims(payload.getSubject(), payload.getEmail(), name,
                picture instanceof String url ? url : null);
    }

    private static boolean empty(String value) {
        return value == null || value.isBlank();
    }

    private static boolean wellFormedAudience(Object audience) {
        return audience instanceof String
                || (audience instanceof List<?> entries && entries.stream().allMatch(String.class::isInstance));
    }

    private static BusinessRuleException rejected() {
        return new BusinessRuleException("ID Token do Google inválido");
    }

    public record GoogleClaims(String googleId, String email, String name, String pictureUrl) {}

    /**
     * O gerenciador do Google cria sua própria HttpRequestFactory. Este transporte aplica
     * os limites diretamente no socket, sem usar os timeouts padrão da fábrica do SDK.
     */
    private static final class CertificateTransport extends HttpTransport {
        private static final int MAX_RESPONSE_BYTES = 64 * 1024;
        private final URI certificates;
        private final int connectTimeout;
        private final int readTimeout;

        CertificateTransport(URI certificates, int connectTimeout, int readTimeout) {
            this.certificates = Objects.requireNonNull(certificates);
            this.connectTimeout = connectTimeout;
            this.readTimeout = readTimeout;
        }

        @Override
        protected LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
            if (!"GET".equals(method) || !certificates.toString().equals(url)) {
                throw new IOException("Destino de certificados inválido.");
            }
            var connection = (HttpURLConnection) certificates.toURL().openConnection();
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setInstanceFollowRedirects(false);
            return new LowLevelHttpRequest() {
                @Override public void addHeader(String name, String value) {
                    connection.addRequestProperty(name, value);
                }

                @Override public LowLevelHttpResponse execute() throws IOException {
                    try {
                        if (connection.getResponseCode() != 200) {
                            throw new IOException("Certificados do Google indisponíveis.");
                        }
                        return new CertificateResponse(connection);
                    } finally {
                        connection.disconnect();
                    }
                }
            };
        }
    }

    private static final class CertificateResponse extends LowLevelHttpResponse {
        private final byte[] body;
        private final List<Map.Entry<String, String>> headers = new ArrayList<>();
        private final String encoding;
        private final String contentType;

        CertificateResponse(HttpURLConnection connection) throws IOException {
            try (var input = connection.getInputStream()) {
                body = input.readNBytes(CertificateTransport.MAX_RESPONSE_BYTES + 1);
                if (body.length > CertificateTransport.MAX_RESPONSE_BYTES) {
                    throw new IOException("Resposta de certificados do Google acima do limite.");
                }
            }
            encoding = connection.getContentEncoding();
            contentType = connection.getContentType();
            connection.getHeaderFields().forEach((name, values) -> {
                if (name != null) values.forEach(value -> headers.add(Map.entry(name, value)));
            });
        }

        @Override public InputStream getContent() { return new ByteArrayInputStream(body); }
        @Override public String getContentEncoding() { return encoding; }
        @Override public long getContentLength() { return body.length; }
        @Override public String getContentType() { return contentType; }
        @Override public String getStatusLine() { return "HTTP/1.1 200 OK"; }
        @Override public int getStatusCode() { return 200; }
        @Override public String getReasonPhrase() { return "OK"; }
        @Override public int getHeaderCount() { return headers.size(); }
        @Override public String getHeaderName(int index) { return headers.get(index).getKey(); }
        @Override public String getHeaderValue(int index) { return headers.get(index).getValue(); }
    }
}
