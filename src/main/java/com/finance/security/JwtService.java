package com.finance.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;

/**
 * Serviço responsável por emitir e validar JWTs internos (HS256).
 * <p>
 * O claim {@code sub} contém o id interno do usuário (não o sub do Google).
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long accessTokenExpiration;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration}") long accessTokenExpiration) {

        SecretKeySpec key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).build();
        this.accessTokenExpiration = accessTokenExpiration;
    }

    /**
     * Gera um access token JWT com sub = userId.
     */
    public String generateAccessToken(Long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTokenExpiration))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Valida e decodifica um access token.
     *
     * @throws org.springframework.security.oauth2.jwt.JwtException se inválido ou
     *                                                              expirado
     */
    public Jwt decode(String token) {
        return decoder.decode(token);
    }

    /**
     * Extrai o userId (Long) do subject do JWT.
     */
    public Long extractUserId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }
}
