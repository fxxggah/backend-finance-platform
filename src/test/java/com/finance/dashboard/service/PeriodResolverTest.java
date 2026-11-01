package com.finance.dashboard.service;

import com.finance.common.enums.Granularity;
import com.finance.common.enums.Period;
import com.finance.dashboard.dto.PeriodRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodResolverTest {

    private final PeriodResolver resolver = new PeriodResolver();

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    // ---------- resolveCurrent ----------

    static Stream<Arguments> currentRanges() {
        return Stream.of(
                // DAY
                Arguments.of("DAY: o próprio dia", Period.DAY, d(2026, 10, 15), d(2026, 10, 15), d(2026, 10, 15)),
                // WEEK (segunda a domingo; 15/10/2026 é quinta-feira)
                Arguments.of("WEEK: quinta-feira", Period.WEEK, d(2026, 10, 15), d(2026, 10, 12), d(2026, 10, 18)),
                Arguments.of("WEEK: segunda-feira", Period.WEEK, d(2026, 10, 12), d(2026, 10, 12), d(2026, 10, 18)),
                Arguments.of("WEEK: domingo", Period.WEEK, d(2026, 10, 18), d(2026, 10, 12), d(2026, 10, 18)),
                Arguments.of("WEEK: atravessando meses", Period.WEEK, d(2026, 11, 1), d(2026, 10, 26), d(2026, 11, 1)),
                // FORTNIGHT
                Arguments.of("FORTNIGHT: dia 10", Period.FORTNIGHT, d(2026, 10, 10), d(2026, 10, 1), d(2026, 10, 15)),
                Arguments.of("FORTNIGHT: dia 15", Period.FORTNIGHT, d(2026, 10, 15), d(2026, 10, 1), d(2026, 10, 15)),
                Arguments.of("FORTNIGHT: dia 16", Period.FORTNIGHT, d(2026, 10, 16), d(2026, 10, 16), d(2026, 10, 31)),
                Arguments.of("FORTNIGHT: fevereiro", Period.FORTNIGHT, d(2026, 2, 20), d(2026, 2, 16), d(2026, 2, 28)),
                Arguments.of("FORTNIGHT: fevereiro bissexto", Period.FORTNIGHT, d(2028, 2, 20), d(2028, 2, 16), d(2028, 2, 29)),
                // MONTH
                Arguments.of("MONTH: fevereiro", Period.MONTH, d(2026, 2, 10), d(2026, 2, 1), d(2026, 2, 28)),
                Arguments.of("MONTH: último dia do mês", Period.MONTH, d(2026, 10, 31), d(2026, 10, 1), d(2026, 10, 31)),
                // QUARTER
                Arguments.of("QUARTER: 1º trimestre", Period.QUARTER, d(2026, 2, 15), d(2026, 1, 1), d(2026, 3, 31)),
                Arguments.of("QUARTER: 2º trimestre (dia 31 em mês de 30 dias)", Period.QUARTER, d(2026, 5, 31), d(2026, 4, 1), d(2026, 6, 30)),
                Arguments.of("QUARTER: 3º trimestre", Period.QUARTER, d(2026, 9, 30), d(2026, 7, 1), d(2026, 9, 30)),
                Arguments.of("QUARTER: 4º trimestre", Period.QUARTER, d(2026, 12, 31), d(2026, 10, 1), d(2026, 12, 31)),
                // SEMESTER
                Arguments.of("SEMESTER: 1º semestre", Period.SEMESTER, d(2026, 6, 30), d(2026, 1, 1), d(2026, 6, 30)),
                Arguments.of("SEMESTER: início do 2º semestre", Period.SEMESTER, d(2026, 7, 1), d(2026, 7, 1), d(2026, 12, 31)),
                Arguments.of("SEMESTER: dia 31 em agosto", Period.SEMESTER, d(2026, 8, 31), d(2026, 7, 1), d(2026, 12, 31)),
                // YEAR
                Arguments.of("YEAR: ano inteiro", Period.YEAR, d(2026, 6, 15), d(2026, 1, 1), d(2026, 12, 31)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("currentRanges")
    @DisplayName("resolveCurrent: deve calcular o intervalo do período a partir da data de referência")
    void resolveCurrent_shouldComputeRange(String scenario, Period period, LocalDate reference,
                                           LocalDate expectedFrom, LocalDate expectedTo) {
        PeriodRange range = resolver.resolveCurrent(period, reference, null, null);

        assertThat(range.from()).isEqualTo(expectedFrom);
        assertThat(range.to()).isEqualTo(expectedTo);
    }

    @Test
    @DisplayName("resolveCurrent: deve usar a data de hoje quando a referência é nula")
    void resolveCurrent_shouldUseToday_whenReferenceIsNull() {
        LocalDate today = LocalDate.now();

        PeriodRange range = resolver.resolveCurrent(Period.DAY, null, null, null);

        assertThat(range.from()).isEqualTo(today);
        assertThat(range.to()).isEqualTo(today);
    }

    @Test
    @DisplayName("resolveCurrent: CUSTOM deve devolver exatamente as datas informadas, ignorando a referência")
    void resolveCurrent_custom_shouldReturnGivenDates() {
        PeriodRange range = resolver.resolveCurrent(Period.CUSTOM, d(2020, 1, 1), d(2026, 10, 1), d(2026, 10, 19));

        assertThat(range.from()).isEqualTo(d(2026, 10, 1));
        assertThat(range.to()).isEqualTo(d(2026, 10, 19));
    }

    @Test
    @DisplayName("resolveCurrent: CUSTOM deve aceitar from igual a to")
    void resolveCurrent_custom_shouldAcceptSingleDay() {
        PeriodRange range = resolver.resolveCurrent(Period.CUSTOM, null, d(2026, 10, 5), d(2026, 10, 5));

        assertThat(range.from()).isEqualTo(d(2026, 10, 5));
        assertThat(range.to()).isEqualTo(d(2026, 10, 5));
    }

    @Test
    @DisplayName("resolveCurrent: CUSTOM deve aceitar exatamente 120 meses")
    void resolveCurrent_custom_shouldAccept120Months() {
        PeriodRange range = resolver.resolveCurrent(Period.CUSTOM, null, d(2010, 1, 1), d(2020, 1, 1));

        assertThat(range.from()).isEqualTo(d(2010, 1, 1));
        assertThat(range.to()).isEqualTo(d(2020, 1, 1));
    }

    @Test
    @DisplayName("resolveCurrent: CUSTOM deve lançar exceção quando passa de 120 meses")
    void resolveCurrent_custom_shouldThrow_whenOver120Months() {
        assertThatThrownBy(() -> resolver.resolveCurrent(Period.CUSTOM, null, d(2010, 1, 1), d(2020, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O período máximo para consulta é de 120 meses");
    }

    @Test
    @DisplayName("resolveCurrent: CUSTOM deve lançar exceção quando from é nulo")
    void resolveCurrent_custom_shouldThrow_whenFromIsNull() {
        assertThatThrownBy(() -> resolver.resolveCurrent(Period.CUSTOM, null, null, d(2026, 10, 19)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Datas inválidas para período CUSTOM");
    }

    @Test
    @DisplayName("resolveCurrent: CUSTOM deve lançar exceção quando to é nulo")
    void resolveCurrent_custom_shouldThrow_whenToIsNull() {
        assertThatThrownBy(() -> resolver.resolveCurrent(Period.CUSTOM, null, d(2026, 10, 1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Datas inválidas para período CUSTOM");
    }

    @Test
    @DisplayName("resolveCurrent: CUSTOM deve lançar exceção quando from é posterior a to")
    void resolveCurrent_custom_shouldThrow_whenFromIsAfterTo() {
        assertThatThrownBy(() -> resolver.resolveCurrent(Period.CUSTOM, null, d(2026, 10, 20), d(2026, 10, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Datas inválidas para período CUSTOM");
    }

    // ---------- resolvePrevious ----------

    static Stream<Arguments> previousRanges() {
        return Stream.of(
                Arguments.of("DAY", Period.DAY,
                        d(2026, 10, 15), d(2026, 10, 15), d(2026, 10, 14), d(2026, 10, 14)),
                Arguments.of("DAY: virada de mês", Period.DAY,
                        d(2026, 11, 1), d(2026, 11, 1), d(2026, 10, 31), d(2026, 10, 31)),
                Arguments.of("WEEK", Period.WEEK,
                        d(2026, 10, 12), d(2026, 10, 18), d(2026, 10, 5), d(2026, 10, 11)),
                Arguments.of("FORTNIGHT: 2ª quinzena -> 1ª quinzena", Period.FORTNIGHT,
                        d(2026, 10, 16), d(2026, 10, 31), d(2026, 10, 1), d(2026, 10, 15)),
                Arguments.of("FORTNIGHT: 1ª quinzena -> 2ª do mês anterior", Period.FORTNIGHT,
                        d(2026, 10, 1), d(2026, 10, 15), d(2026, 9, 16), d(2026, 9, 30)),
                Arguments.of("FORTNIGHT: março -> fevereiro", Period.FORTNIGHT,
                        d(2026, 3, 1), d(2026, 3, 15), d(2026, 2, 16), d(2026, 2, 28)),
                Arguments.of("MONTH: março -> fevereiro", Period.MONTH,
                        d(2026, 3, 1), d(2026, 3, 31), d(2026, 2, 1), d(2026, 2, 28)),
                Arguments.of("MONTH: janeiro -> dezembro do ano anterior", Period.MONTH,
                        d(2026, 1, 1), d(2026, 1, 31), d(2025, 12, 1), d(2025, 12, 31)),
                Arguments.of("QUARTER: 2º -> 1º trimestre", Period.QUARTER,
                        d(2026, 4, 1), d(2026, 6, 30), d(2026, 1, 1), d(2026, 3, 31)),
                Arguments.of("QUARTER: 1º trimestre -> 4º do ano anterior", Period.QUARTER,
                        d(2026, 1, 1), d(2026, 3, 31), d(2025, 10, 1), d(2025, 12, 31)),
                Arguments.of("SEMESTER: 2º -> 1º semestre", Period.SEMESTER,
                        d(2026, 7, 1), d(2026, 12, 31), d(2026, 1, 1), d(2026, 6, 30)),
                Arguments.of("YEAR", Period.YEAR,
                        d(2026, 1, 1), d(2026, 12, 31), d(2025, 1, 1), d(2025, 12, 31)),
                Arguments.of("CUSTOM: mesmo número de dias imediatamente antes", Period.CUSTOM,
                        d(2026, 10, 10), d(2026, 10, 19), d(2026, 9, 30), d(2026, 10, 9)),
                Arguments.of("CUSTOM: um único dia", Period.CUSTOM,
                        d(2026, 10, 10), d(2026, 10, 10), d(2026, 10, 9), d(2026, 10, 9)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("previousRanges")
    @DisplayName("resolvePrevious: deve calcular o período imediatamente anterior")
    void resolvePrevious_shouldComputePreviousRange(String scenario, Period period,
                                                    LocalDate currentFrom, LocalDate currentTo,
                                                    LocalDate expectedFrom, LocalDate expectedTo) {
        PeriodRange previous = resolver.resolvePrevious(period, new PeriodRange(currentFrom, currentTo));

        assertThat(previous.from()).isEqualTo(expectedFrom);
        assertThat(previous.to()).isEqualTo(expectedTo);
    }

    // ---------- resolveGranularity ----------

    static Stream<Arguments> granularities() {
        return Stream.of(
                Arguments.of(1, Granularity.DAY),
                Arguments.of(31, Granularity.DAY),
                Arguments.of(32, Granularity.WEEK),
                Arguments.of(100, Granularity.WEEK),
                Arguments.of(101, Granularity.MONTH),
                Arguments.of(365, Granularity.MONTH));
    }

    @ParameterizedTest(name = "{0} dia(s) -> {1}")
    @MethodSource("granularities")
    @DisplayName("resolveGranularity: deve escolher a granularidade pela quantidade de dias do intervalo")
    void resolveGranularity_shouldDependOnNumberOfDays(int days, Granularity expected) {
        LocalDate from = d(2026, 1, 1);
        PeriodRange range = new PeriodRange(from, from.plusDays(days - 1L));

        assertThat(resolver.resolveGranularity(range)).isEqualTo(expected);
    }
}