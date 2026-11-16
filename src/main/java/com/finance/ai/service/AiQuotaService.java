package com.finance.ai.service;

import com.finance.ai.config.AiQuotaProperties;
import com.finance.ai.entity.AiGlobalAttempt;
import com.finance.ai.entity.AiUsageRequest;
import com.finance.ai.exception.AiException;
import com.finance.ai.repository.*;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import static com.finance.ai.entity.AiUsageRequest.Status.*;

@Service
public class AiQuotaService {
    private static final ZoneId USER_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final ZoneId PROVIDER_ZONE = ZoneId.of("America/Los_Angeles");
    private final UserRepository users;
    private final AiQuotaGuardRepository guards;
    private final AiUsageRequestRepository requests;
    private final AiGlobalAttemptRepository attempts;
    private final AiQuotaProperties limits;
    private final Clock clock;

    public AiQuotaService(UserRepository users, AiQuotaGuardRepository guards, AiUsageRequestRepository requests,
            AiGlobalAttemptRepository attempts, AiQuotaProperties limits, Clock clock) {
        this.users = users; this.guards = guards; this.requests = requests;
        this.attempts = attempts; this.limits = limits; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void checkAccess(Long userId) { activePro(userId, clock.instant()); }

    public static void validateKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,128}")) throw new AiException(HttpStatus.BAD_REQUEST,
                "INVALID_IDEMPOTENCY_KEY", "Informe Idempotency-Key com 8 a 128 caracteres válidos, preferencialmente um UUID.");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long reserve(Long userId, String key, byte[] audio) {
        validateKey(key);
        var guard = guards.lockGuard();
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        User user = activePro(userId, now);
        String keyHash = hash(key.getBytes(StandardCharsets.UTF_8)), audioHash = hash(audio);
        requests.findByUserIdAndKeyHash(userId, keyHash).ifPresent(existing -> {
            if (!existing.getAudioHash().equals(audioHash)) throw conflict("IDEMPOTENCY_CONFLICT", "A chave já foi usada com outro áudio.");
            if (existing.getStatus() == PROCESSING && existing.getLeaseUntil().isAfter(now)) {
                throw conflict("AI_REQUEST_IN_PROGRESS", "Já existe uma solicitação com essa chave em andamento.");
            }
            throw conflict("AI_REQUEST_ALREADY_USED", "Essa chave já foi utilizada. Para tentar novamente, envie uma nova chave.");
        });
        if (requests.existsByUserIdAndStatusAndLeaseUntilGreaterThan(userId, PROCESSING, now)) {
            throw conflict("AI_REQUEST_IN_PROGRESS", "Aguarde sua solicitação de IA em andamento.");
        }
        if (guard.getBlockedUntil() != null && guard.getBlockedUntil().isAfter(now)) {
            throw new AiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_RATE_LIMITED", "A IA está temporariamente limitada. Preencha manualmente.", seconds(now, guard.getBlockedUntil()));
        }
        LocalDate userDay = now.atZone(USER_ZONE).toLocalDate();
        if (requests.countByUserIdAndStatusAndSuccessDay(userId, SUCCEEDED, userDay) >= limits.getUserSuccessesPerDay()) {
            throw new AiException(HttpStatus.TOO_MANY_REQUESTS, "USER_AI_QUOTA_EXCEEDED", "Sua cota diária de IA foi atingida.",
                    seconds(now, userDay.plusDays(1).atStartOfDay(USER_ZONE).toInstant()));
        }
        if (attempts.countByReservedAtGreaterThanAndReservedAtLessThanEqual(now.minusSeconds(60), now) >= limits.getGlobalAttemptsPerMinute()) {
            throw new AiException(HttpStatus.TOO_MANY_REQUESTS, "GLOBAL_AI_CAPACITY_EXCEEDED", "A capacidade de IA está ocupada. Tente depois ou preencha manualmente.", 60L);
        }
        LocalDate providerDay = now.atZone(PROVIDER_ZONE).toLocalDate();
        Instant providerStart = providerDay.atStartOfDay(PROVIDER_ZONE).toInstant();
        Instant providerEnd = providerDay.plusDays(1).atStartOfDay(PROVIDER_ZONE).toInstant();
        if (attempts.countByReservedAtGreaterThanEqualAndReservedAtLessThan(providerStart, providerEnd) >= limits.getGlobalAttemptsPerDay()) {
            throw new AiException(HttpStatus.TOO_MANY_REQUESTS, "GLOBAL_AI_CAPACITY_EXCEEDED", "A capacidade diária de IA foi atingida. Preencha manualmente.", seconds(now, providerEnd));
        }
        requests.expire(userId, now, PROCESSING, FAILED);
        var attempt = new AiGlobalAttempt(); attempt.setReservedAt(now); attempts.save(attempt);
        var request = new AiUsageRequest(); request.setUser(user); request.setKeyHash(keyHash); request.setAudioHash(audioHash);
        request.setStatus(PROCESSING); request.setReservedAt(now); request.setLeaseUntil(now.plusSeconds(limits.getLeaseSeconds()));
        return requests.save(request).getId();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void succeed(Long userId, Long requestId) {
        guards.lockGuard();
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var request = requests.findByIdAndUserId(requestId, userId).orElseThrow(AiQuotaService::expired);
        if (request.getStatus() != PROCESSING || !request.getLeaseUntil().isAfter(now)) throw expired();
        activePro(userId, now);
        LocalDate day = now.atZone(USER_ZONE).toLocalDate();
        if (requests.countByUserIdAndStatusAndSuccessDay(userId, SUCCEEDED, day) >= limits.getUserSuccessesPerDay()) throw expired();
        request.setStatus(SUCCEEDED); request.setSuccessDay(day);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void fail(Long userId, Long requestId, boolean providerRateLimited) {
        var guard = guards.lockGuard();
        requests.findByIdAndUserId(requestId, userId).filter(value -> value.getStatus() == PROCESSING)
                .ifPresent(value -> value.setStatus(FAILED));
        if (providerRateLimited) {
            Instant until = clock.instant().plusSeconds(limits.getProviderCooldownSeconds());
            if (guard.getBlockedUntil() == null || until.isAfter(guard.getBlockedUntil())) guard.setBlockedUntil(until);
        }
    }

    private User activePro(Long userId, Instant now) {
        User user = users.findById(userId).filter(User::isActive).orElseThrow(() ->
                new AiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuário não encontrado."));
        if (user.getProExpiresAt() == null || !user.getProExpiresAt().isAfter(now)) {
            throw new AiException(HttpStatus.FORBIDDEN, "PRO_REQUIRED", "A gravação por voz está disponível somente para PRO com validade ativa.");
        }
        return user;
    }
    private static AiException conflict(String code, String message) { return new AiException(HttpStatus.CONFLICT, code, message); }
    private static AiException expired() {
        return new AiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_REQUEST_EXPIRED", "A solicitação expirou. Tente novamente com uma nova chave.");
    }
    private static long seconds(Instant now, Instant until) { return Math.max(1, Duration.between(now, until).toSeconds() + 1); }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 indisponível"); }
    }
}
