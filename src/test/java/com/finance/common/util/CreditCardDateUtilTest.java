package com.finance.common.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CreditCardDateUtilTest {
    private final CreditCardDateUtil dates = new CreditCardDateUtil();

    @ParameterizedTest
    @CsvSource({
            "2026-11-01, 10, 15, 2026-11-10, 2026-11-15",
            "2026-11-01, 10, 10, 2026-11-10, 2026-12-10",
            "2026-11-01, 10,  5, 2026-11-10, 2026-12-05",
            "2026-12-01, 28, 28, 2026-12-28, 2027-01-28",
            "2028-02-01,  1, 28, 2028-02-01, 2028-02-28"
    })
    void resolvesClosingAndDueDatesFromInvoiceMonth(LocalDate reference, int closingDay, int dueDay,
                                                   LocalDate closing, LocalDate due) {
        assertThat(dates.closingDate(reference, closingDay)).isEqualTo(closing);
        assertThat(dates.dueDate(reference, closingDay, dueDay)).isEqualTo(due);
    }
}
