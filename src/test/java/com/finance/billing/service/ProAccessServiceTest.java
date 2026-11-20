package com.finance.billing.service;

import com.finance.billing.repository.BillingInvoiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProAccessServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-12-01T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-12-20T12:00:00Z");

    @ParameterizedTest
    @MethodSource("validities")
    void combinesManualAndPaidValidityWithoutAddingTime(Instant manual, Instant paid, Instant expected) {
        assertThat(ProAccessService.max(manual, paid)).isEqualTo(expected);
    }

    static Stream<Arguments> validities() {
        return Stream.of(
                Arguments.of(null, null, null),
                Arguments.of(EARLIER, null, EARLIER),
                Arguments.of(null, LATER, LATER),
                Arguments.of(EARLIER, LATER, LATER),
                Arguments.of(LATER, EARLIER, LATER),
                Arguments.of(LATER, LATER, LATER));
    }

    @Test
    void readsPaidValidityForTheRequestedUserAndInstant() {
        var invoices = mock(BillingInvoiceRepository.class);
        when(invoices.paidUntil(42L, NOW)).thenReturn(LATER);
        var service = new ProAccessService(invoices);

        assertThat(service.paidUntil(42L, NOW)).isEqualTo(LATER);
        verify(invoices).paidUntil(42L, NOW);
    }
}
