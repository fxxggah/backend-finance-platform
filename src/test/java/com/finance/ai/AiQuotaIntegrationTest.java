package com.finance.ai;

import com.finance.ai.config.*;
import com.finance.ai.entity.AiUsageRequest;
import com.finance.ai.exception.AiException;
import com.finance.ai.repository.*;
import com.finance.ai.service.AiQuotaService;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static com.finance.ai.entity.AiUsageRequest.Status.*;

@DataJpaTest(showSql = false, properties = {"spring.config.name=ai-quota-test", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AiQuotaService.class, AiQuotaConfiguration.class, com.finance.billing.service.ProAccessService.class, AiQuotaIntegrationTest.TimeConfig.class})
class AiQuotaIntegrationTest {
    @Container @ServiceConnection static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    static class MutableClock extends Clock {
        final AtomicReference<Instant> time = new AtomicReference<>();
        void set(String value) { time.set(Instant.parse(value)); }
        void advance(long seconds) { time.updateAndGet(value -> value.plusSeconds(seconds)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return time.get(); }
    }
    @TestConfiguration @EnableJpaAuditing static class TimeConfig {
        @Bean @Primary MutableClock quotaClock() { return new MutableClock(); }
    }
    @Autowired AiQuotaService quota;
    @Autowired com.finance.billing.service.ProAccessService proAccess;
    @Autowired AiQuotaProperties limits;
    @Autowired AiUsageRequestRepository requests;
    @Autowired AiGlobalAttemptRepository attempts;
    @Autowired AiQuotaGuardRepository guards;
    @Autowired UserRepository users;
    @Autowired MutableClock clock;
    @Autowired PlatformTransactionManager transactionManager;
    private static final byte[] AUDIO = new byte[]{1, 2, 3};

    @BeforeEach void setup() {
        requests.deleteAll(); attempts.deleteAll();
        var guard = guards.findById(1L).orElseThrow(); guard.setBlockedUntil(null); guards.saveAndFlush(guard);
        clock.set("2026-11-16T12:00:00Z");
        limits.setUserSuccessesPerDay(10); limits.setGlobalAttemptsPerMinute(1000); limits.setGlobalAttemptsPerDay(10000);
        limits.setLeaseSeconds(120); limits.setProviderCooldownSeconds(60);
    }
    private Long pro() {
        String id = UUID.randomUUID().toString();
        User user = new User(id, id + "@example.com", "Synthetic", null);
        user.setProExpiresAt(Instant.parse("2027-12-31T00:00:00Z"));
        return users.saveAndFlush(user).getId();
    }
    private Long reserve(Long userId) { return quota.reserve(userId, UUID.randomUUID().toString(), AUDIO); }
    private void error(Runnable operation, String code) {
        assertThatThrownBy(operation::run).isInstanceOf(AiException.class).extracting("code").isEqualTo(code);
    }

    @Test void basicInactiveAndExactlyExpiredCannotReserve() {
        Long id = pro(); User user = users.findById(id).orElseThrow();
        user.setProExpiresAt(null); users.saveAndFlush(user); error(() -> reserve(id), "PRO_REQUIRED");
        user.setProExpiresAt(clock.instant()); users.saveAndFlush(user); error(() -> reserve(id), "PRO_REQUIRED");
        user.setActive(false); users.saveAndFlush(user); error(() -> reserve(id), "USER_NOT_FOUND");
        assertThat(attempts.count()).isZero(); assertThat(requests.count()).isZero();
    }

    @Test void onlySuccessConsumesPersonalQuotaButEveryAdmittedAttemptConsumesGlobalBudget() {
        Long id = pro();
        for (int i = 0; i < 10; i++) quota.fail(id, reserve(id), false);
        assertThat(requests.countByUserIdAndStatusAndSuccessDay(id, SUCCEEDED, LocalDate.of(2026, 11, 16))).isZero();
        for (int i = 0; i < 10; i++) quota.succeed(id, reserve(id));
        error(() -> reserve(id), "USER_AI_QUOTA_EXCEEDED");
        assertThat(attempts.count()).isEqualTo(20);
        assertThat(requests.countByUserIdAndStatusAndSuccessDay(id, SUCCEEDED, LocalDate.of(2026, 11, 16))).isEqualTo(10);
    }

    @Test void idempotencyAndAudioBindingAreScopedToUserAndNeverReplay() {
        Long first = pro(), second = pro(); String key = "same-key-0001";
        Long reservation = quota.reserve(first, key, AUDIO);
        error(() -> quota.reserve(first, key, AUDIO), "AI_REQUEST_IN_PROGRESS");
        error(() -> quota.reserve(first, key, new byte[]{9}), "IDEMPOTENCY_CONFLICT");
        Long independent = quota.reserve(second, key, AUDIO);
        error(() -> quota.succeed(second, reservation), "AI_REQUEST_EXPIRED");
        quota.fail(second, reservation, false);
        assertThat(requests.findByIdAndUserId(reservation, first).orElseThrow().getStatus()).isEqualTo(PROCESSING);
        quota.fail(first, reservation, false);
        error(() -> quota.reserve(first, key, AUDIO), "AI_REQUEST_ALREADY_USED");
        quota.succeed(second, independent);
        error(() -> quota.reserve(second, key, AUDIO), "AI_REQUEST_ALREADY_USED");
        assertThat(attempts.count()).isEqualTo(2);
    }

    private List<Boolean> concurrent(List<Long> ids, boolean secondInstance) throws Exception {
        var start = new CountDownLatch(1);
        var other = new AiQuotaService(users, guards, requests, attempts, limits, clock, proAccess);
        var template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try (var pool = Executors.newFixedThreadPool(ids.size())) {
            List<Future<Boolean>> futures = IntStream.range(0, ids.size()).mapToObj(index -> pool.submit(() -> {
                start.await();
                try {
                    if (secondInstance && index % 2 == 0) template.execute(status -> other.reserve(ids.get(index), UUID.randomUUID().toString(), AUDIO));
                    else reserve(ids.get(index));
                    return true;
                } catch (AiException expected) {
                    assertThat(expected.code()).isIn("AI_REQUEST_IN_PROGRESS", "GLOBAL_AI_CAPACITY_EXCEEDED", "USER_AI_QUOTA_EXCEEDED");
                    return false;
                }
            })).toList();
            start.countDown();
            List<Boolean> result = new ArrayList<>();
            for (var future : futures) result.add(future.get(30, TimeUnit.SECONDS));
            return result;
        }
    }

    @Test void simultaneousRequestsForSameUserReserveExactlyOneSlot() throws Exception {
        Long id = pro();
        assertThat(concurrent(Collections.nCopies(12, id), false)).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(attempts.count()).isEqualTo(1); assertThat(requests.count()).isEqualTo(1);
    }

    @Test void twoServiceInstancesCannotExceedGlobalMinuteBudget() throws Exception {
        limits.setGlobalAttemptsPerMinute(3);
        List<Long> ids = IntStream.range(0, 12).mapToObj(index -> pro()).toList();
        assertThat(concurrent(ids, true)).filteredOn(Boolean::booleanValue).hasSize(3);
        assertThat(attempts.count()).isEqualTo(3);
        clock.advance(60); assertThat(reserve(pro())).isNotNull();
    }

    @Test void parallelCallsCannotSpendTheLastPersonalSuccessTwice() throws Exception {
        Long id = pro(); for (int i = 0; i < 9; i++) quota.succeed(id, reserve(id));
        assertThat(concurrent(Collections.nCopies(12, id), true)).filteredOn(Boolean::booleanValue).hasSize(1);
        var active = requests.findAll().stream().filter(value -> value.getStatus() == PROCESSING).findFirst().orElseThrow();
        quota.succeed(id, active.getId()); error(() -> reserve(id), "USER_AI_QUOTA_EXCEEDED");
        assertThat(requests.countByUserIdAndStatusAndSuccessDay(id, SUCCEEDED, LocalDate.of(2026, 11, 16))).isEqualTo(10);
    }

    @Test void expiredLeaseReleasesSlotWithoutAllowingLateSuccess() {
        Long id = pro(), old = reserve(id); clock.advance(120); Long replacement = reserve(id);
        error(() -> quota.succeed(id, old), "AI_REQUEST_EXPIRED");
        quota.succeed(id, replacement);
        assertThat(requests.findByIdAndUserId(old, id).orElseThrow().getStatus()).isEqualTo(FAILED);
        assertThat(attempts.count()).isEqualTo(2);
    }

    @Test void revocationDuringCallPreventsSuccessAndFailureReleasesSlot() {
        Long id = pro(), request = reserve(id); var user = users.findById(id).orElseThrow();
        user.setProExpiresAt(clock.instant()); users.saveAndFlush(user);
        error(() -> quota.succeed(id, request), "PRO_REQUIRED"); quota.fail(id, request, false);
        assertThat(requests.findByIdAndUserId(request, id).orElseThrow().getStatus()).isEqualTo(FAILED);
    }

    @Test void successUsesCompletionDayInSaoPaulo() {
        clock.set("2026-11-16T02:59:59Z"); Long id = pro(); quota.succeed(id, reserve(id));
        Long pending = reserve(id); clock.advance(2); quota.succeed(id, pending);
        assertThat(requests.countByUserIdAndStatusAndSuccessDay(id, SUCCEEDED, LocalDate.of(2026, 11, 15))).isEqualTo(1);
        assertThat(requests.countByUserIdAndStatusAndSuccessDay(id, SUCCEEDED, LocalDate.of(2026, 11, 16))).isEqualTo(1);
    }

    @Test void globalDayResetsInPacificAndDoesNotResetAtSaoPauloMidnight() {
        limits.setGlobalAttemptsPerDay(1); clock.set("2026-11-16T02:59:59Z"); Long id = pro();
        quota.fail(id, reserve(id), false); clock.advance(2); error(() -> reserve(id), "GLOBAL_AI_CAPACITY_EXCEEDED");
        clock.set("2026-11-16T08:00:00Z"); assertThat(reserve(id)).isNotNull();
    }

    @Test void providerCooldownPersistsWithoutSpendingFurtherAttempts() {
        Long id = pro(); quota.fail(id, reserve(id), true);
        error(() -> reserve(pro()), "AI_RATE_LIMITED"); assertThat(attempts.count()).isEqualTo(1);
        assertThat(guards.findById(1L).orElseThrow().getBlockedUntil()).isEqualTo(clock.instant().plusSeconds(60));
        clock.advance(60); assertThat(reserve(pro())).isNotNull();
    }

    @Test void deletingUserRemovesPrivateHashesWithoutRefundingGlobalCapacity() {
        Long id = pro(); quota.succeed(id, reserve(id)); users.deleteById(id);
        assertThat(requests.count()).isZero(); assertThat(attempts.count()).isEqualTo(1);
    }
}
