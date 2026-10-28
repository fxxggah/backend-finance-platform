package com.finance.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.common.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Valida o ID Token emitido pelo Google Sign-In.
 * <p>
 * Usa o endpoint público do Google {@code /oauth2/v3/tokeninfo} para validar a
 * assinatura
 * sem precisar do SDK {@code google-api-client}. O endpoint é chamado apenas
 * durante o login.
 * <ul>
 * <li>Valida assinatura (feita pelo Google)</li>
 * <li>Valida {@code aud} contra o GOOGLE_CLIENT_ID configurado</li>
 * <li>Valida {@code iss} (accounts.google.com)</li>
 * <li>Valida expiração</li>
 * <li>Exige {@code email_verified = true}</li>
 * </ul>
 */
@Component
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);
    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private final String googleClientId;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GoogleTokenVerifier(
            @Value("${app.google.client-id}") String googleClientId) {
        this.googleClientId = googleClientId;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Valida o ID Token e retorna os claims do usuário.
     *
     * @param idToken o ID Token recebido do frontend
     * @return {@link GoogleClaims} com sub, email, name e picture
     * @throws BusinessRuleException se o token for inválido
     */
    public GoogleClaims verify(String idToken) {
        GoogleTokenInfoResponse info = fetchTokenInfo(idToken);
        validateClaims(info);
        return new GoogleClaims(
                info.sub(),
                info.email(),
                info.name(),
                info.picture());
    }

    private GoogleTokenInfoResponse fetchTokenInfo(String idToken) {
        String encodedToken = URLEncoder.encode(idToken, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKENINFO_URL + encodedToken))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Google tokeninfo retornou status {}: {}", response.statusCode(), response.body());
                throw new BusinessRuleException("ID Token do Google inválido");
            }
            return objectMapper.readValue(response.body(), GoogleTokenInfoResponse.class);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Erro ao validar ID Token no Google: {}", e.getMessage());
            throw new BusinessRuleException("Não foi possível validar o token do Google");
        }
    }

    private void validateClaims(GoogleTokenInfoResponse info) {
        // Valida audience
        if (!googleClientId.equals(info.aud())) {
            throw new BusinessRuleException("ID Token não pertence a este cliente");
        }

        // Valida issuer
        if (info.iss() == null ||
                (!info.iss().equals("accounts.google.com") && !info.iss().equals("https://accounts.google.com"))) {
            throw new BusinessRuleException("Emissor do ID Token inválido");
        }

        // Valida expiração
        if (info.exp() == null || Instant.ofEpochSecond(info.exp()).isBefore(Instant.now())) {
            throw new BusinessRuleException("ID Token expirado");
        }

        // Exige email verificado
        if (!"true".equals(info.emailVerified())) {
            throw new BusinessRuleException("E-mail do Google não verificado");
        }
    }

    /**
     * Resposta do endpoint {@code /tokeninfo} do Google.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoogleTokenInfoResponse(
            String sub,
            String email,
            @JsonProperty("email_verified") String emailVerified,
            String name,
            String picture,
            String aud,
            String iss,
            Long exp) {
    }

    /**
     * Claims relevantes extraídos do ID Token após validação.
     */
    public record GoogleClaims(
            String googleId,
            String email,
            String name,
            String pictureUrl) {
    }
}
