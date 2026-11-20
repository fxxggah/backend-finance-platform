package com.finance.ai.service;

import com.finance.ai.config.AiQuotaProperties;
import com.finance.ai.entity.AiGlobalAttempt;
import com.finance.ai.entity.AiQuotaGuard;
import com.finance.ai.entity.AiUsageRequest;
import com.finance.ai.exception.AiException;
import com.finance.ai.repository.AiGlobalAttemptRepository;
import com.finance.ai.repository.AiQuotaGuardRepository;
import com.finance.ai.repository.AiUsageRequestRepository;
import com.finance.billing.service.ProAccessService;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.finance.ai.entity.AiUsageRequest.Status.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiQuotaServiceTest {
    private static final long USER = 7L;
    private static final long REQUEST = 15L;
    private static final String KEY = "quota-test-key";
    private static final byte[] AUDIO = {1, 2, 3};
    private static final Instant NOW = Instant.parse("2026-12-01T03:00:00Z");

    @Mock private UserRepository users;
    @Mock private AiQuotaGuardRepository guards;
    @Mock private AiUsageRequestRepository requests;
    @Mock private AiGlobalAttemptRepository attempts;
    @Mock private ProAccessService proAccess;

    @ParameterizedTest
    @ValueSource(ints = {8, 128})
    void acceptsKeysAtBothLengthLimits(int length) {
        assertThatCode(() -> AiQuotaService.validateKey("a".repeat(length))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1234567", "key with space", "key/with/slash"})
    void rejectsMalformedKeyBeforeTakingAnyLock(String key) {
        assertError(() -> serviceAt(NOW).reserve(USER, key, AUDIO),
                HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", null);
        verifyNoInteractions(guards, users, requests, attempts, proAccess);
    }

    @Test
    void rejectsKeyOneCharacterOverLimitBeforeTakingAnyLock() {
        assertError(() -> serviceAt(NOW).reserve(USER, "a".repeat(129), AUDIO),
                HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", null);
        verifyNoInteractions(guards, users, requests, attempts, proAccess);
    }

    @Test
    void rejectsProAtExactExpiration() {
        mockActiveUser(NOW);
        assertError(() -> serviceAt(NOW).checkAccess(USER), HttpStatus.FORBIDDEN, "PRO_REQUIRED", null);
        verifyNoInteractions(guards, requests, attempts);
    }

    @Test
    void personalQuotaReturnsSecondsUntilSaoPauloMidnightWithoutSpendingGlobalBudget() {
        Instant now = Instant.parse("2026-12-01T02:59:58.500Z");
        mockActiveUser(now.plusSeconds(600));
        when(guards.lockGuard()).thenReturn(new AiQuotaGuard());
        when(requests.countByUserIdAndStatusAndSuccessDay(USER, SUCCEEDED, LocalDate.of(2026, 11, 30)))
                .thenReturn(10L);

        assertError(() -> serviceAt(now).reserve(USER, KEY, AUDIO),
                HttpStatus.TOO_MANY_REQUESTS, "USER_AI_QUOTA_EXCEEDED", 2L);
        verifyNoInteractions(attempts);
        verify(requests, never()).save(any());
    }

    @Test
    void globalMinuteQuotaUsesRollingWindowAndStopsBeforeDailyBudget() {
        mockActiveUser(NOW.plusSeconds(600));
        when(guards.lockGuard()).thenReturn(new AiQuotaGuard());
        when(attempts.countByReservedAtGreaterThanAndReservedAtLessThanEqual(NOW.minusSeconds(60), NOW))
                .thenReturn(10L);

        assertError(() -> serviceAt(NOW).reserve(USER, KEY, AUDIO),
                HttpStatus.TOO_MANY_REQUESTS, "GLOBAL_AI_CAPACITY_EXCEEDED", 60L);
        verify(attempts, never()).countByReservedAtGreaterThanEqualAndReservedAtLessThan(any(), any());
        verifyNoReservation();
    }

    @Test
    void globalDailyQuotaReturnsSecondsUntilPacificMidnight() {
        Instant now = Instant.parse("2026-12-01T07:59:58.500Z");
        mockActiveUser(now.plusSeconds(600));
        when(guards.lockGuard()).thenReturn(new AiQuotaGuard());
        var start = Instant.parse("2026-11-30T08:00:00Z");
        var end = Instant.parse("2026-12-01T08:00:00Z");
        when(attempts.countByReservedAtGreaterThanEqualAndReservedAtLessThan(start, end)).thenReturn(400L);

        assertError(() -> serviceAt(now).reserve(USER, KEY, AUDIO),
                HttpStatus.TOO_MANY_REQUESTS, "GLOBAL_AI_CAPACITY_EXCEEDED", 2L);
        verify(requests).countByUserIdAndStatusAndSuccessDay(USER, SUCCEEDED, LocalDate.of(2026, 12, 1));
        verifyNoReservation();
    }

    @Test
    void activeCooldownReturnsRetryAfterWithoutConsumingQuota() {
        mockActiveUser(NOW.plusSeconds(600));
        var guard = new AiQuotaGuard();
        guard.setBlockedUntil(NOW.plusMillis(1500));
        when(guards.lockGuard()).thenReturn(guard);

        assertError(() -> serviceAt(NOW).reserve(USER, KEY, AUDIO),
                HttpStatus.SERVICE_UNAVAILABLE, "AI_RATE_LIMITED", 2L);
        verify(requests, never()).countByUserIdAndStatusAndSuccessDay(any(), any(), any());
        verifyNoInteractions(attempts);
        verify(requests, never()).save(any());
    }

    @Test
    void admitsReservationAtExactCooldownEndInNewPacificDay() {
        Instant now = Instant.parse("2026-12-01T08:00:00Z");
        var user = mockActiveUser(now.plusSeconds(600));
        var guard = new AiQuotaGuard();
        guard.setBlockedUntil(now);
        when(guards.lockGuard()).thenReturn(guard);
        when(requests.save(any(AiUsageRequest.class))).thenAnswer(invocation -> {
            AiUsageRequest saved = invocation.getArgument(0);
            saved.setId(REQUEST);
            return saved;
        });

        assertThat(serviceAt(now).reserve(USER, KEY, AUDIO)).isEqualTo(REQUEST);

        var attempt = ArgumentCaptor.forClass(AiGlobalAttempt.class);
        var reservation = ArgumentCaptor.forClass(AiUsageRequest.class);
        verify(attempts).countByReservedAtGreaterThanEqualAndReservedAtLessThan(
                now, Instant.parse("2026-12-02T08:00:00Z"));
        verify(attempts).save(attempt.capture());
        verify(requests).expire(USER, now, PROCESSING, FAILED);
        verify(requests).save(reservation.capture());
        assertThat(attempt.getValue().getReservedAt()).isEqualTo(now);
        assertThat(reservation.getValue().getUser()).isSameAs(user);
        assertThat(reservation.getValue().getStatus()).isEqualTo(PROCESSING);
        assertThat(reservation.getValue().getLeaseUntil()).isEqualTo(now.plusSeconds(120));
    }

    @Test
    void successAtExactLeaseExpirationDoesNotConsumePersonalQuota() {
        var request = processing(NOW);
        when(requests.findByIdAndUserId(REQUEST, USER)).thenReturn(Optional.of(request));

        assertError(() -> serviceAt(NOW).succeed(USER, REQUEST),
                HttpStatus.SERVICE_UNAVAILABLE, "AI_REQUEST_EXPIRED", null);
        assertThat(request.getStatus()).isEqualTo(PROCESSING);
        assertThat(request.getSuccessDay()).isNull();
        verify(requests, never()).countByUserIdAndStatusAndSuccessDay(any(), any(), any());
        verifyNoInteractions(users, attempts, proAccess);
    }

    @Test
    void successBeforeLeaseExpirationUsesLocalCompletionDate() {
        var request = processing(NOW.plusSeconds(1));
        request.setReservedAt(NOW.minusSeconds(15));
        when(requests.findByIdAndUserId(REQUEST, USER)).thenReturn(Optional.of(request));
        mockActiveUser(NOW.plusSeconds(60));

        serviceAt(NOW).succeed(USER, REQUEST);

        assertThat(request.getStatus()).isEqualTo(SUCCEEDED);
        assertThat(request.getSuccessDay()).isEqualTo(LocalDate.of(2026, 12, 1));
        verify(requests).countByUserIdAndStatusAndSuccessDay(USER, SUCCEEDED, LocalDate.of(2026, 12, 1));
    }

    @ParameterizedTest
    @CsvSource({"30, 60", "60, 60", "90, 90"})
    void providerRateLimitKeepsGreaterCooldownAndMarksReservationFailed(long previousCooldown, long expectedCooldown) {
        var guard = new AiQuotaGuard();
        guard.setBlockedUntil(NOW.plusSeconds(previousCooldown));
        var request = processing(NOW.plusSeconds(120));
        when(guards.lockGuard()).thenReturn(guard);
        when(requests.findByIdAndUserId(REQUEST, USER)).thenReturn(Optional.of(request));

        serviceAt(NOW).fail(USER, REQUEST, true);

        assertThat(guard.getBlockedUntil()).isEqualTo(NOW.plusSeconds(expectedCooldown));
        assertThat(request.getStatus()).isEqualTo(FAILED);
        assertThat(request.getSuccessDay()).isNull();
        verifyNoInteractions(attempts);
    }

    @Test
    void ordinaryFailureDoesNotStartCooldownOrOverwriteFinishedReservation() {
        var guard = new AiQuotaGuard();
        var request = processing(NOW.plusSeconds(120));
        request.setStatus(SUCCEEDED);
        request.setSuccessDay(LocalDate.of(2026, 12, 1));
        when(guards.lockGuard()).thenReturn(guard);
        when(requests.findByIdAndUserId(REQUEST, USER)).thenReturn(Optional.of(request));

        serviceAt(NOW).fail(USER, REQUEST, false);

        assertThat(guard.getBlockedUntil()).isNull();
        assertThat(request.getStatus()).isEqualTo(SUCCEEDED);
        assertThat(request.getSuccessDay()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    private AiQuotaService serviceAt(Instant now) {
        return new AiQuotaService(users, guards, requests, attempts, new AiQuotaProperties(),
                Clock.fixed(now, ZoneOffset.UTC), proAccess);
    }

    private User mockActiveUser(Instant expires) {
        var user = new User("synthetic", "test@example.test", "Test", null);
        user.setProExpiresAt(expires);
        when(users.findById(USER)).thenReturn(Optional.of(user));
        return user;
    }

    private static AiUsageRequest processing(Instant expiry) {
        var request = new AiUsageRequest();
        request.setStatus(PROCESSING);
        request.setLeaseUntil(expiry);
        return request;
    }

    private void verifyNoReservation() {
        verify(attempts, never()).save(any());
        verify(requests, never()).save(any());
        verify(requests, never()).expire(any(), any(), any(), any());
    }

    private static void assertError(Runnable action, HttpStatus status, String code, Long retryAfter) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AiException.class, error -> {
            assertThat(error.status()).isEqualTo(status);
            assertThat(error.code()).isEqualTo(code);
            assertThat(error.retryAfterSeconds()).isEqualTo(retryAfter);
        });
    }
}
