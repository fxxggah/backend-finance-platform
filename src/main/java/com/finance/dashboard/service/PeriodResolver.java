package com.finance.dashboard.service;

import com.finance.common.enums.Period;
import com.finance.common.enums.Granularity;
import com.finance.dashboard.dto.PeriodRange;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

@Component
public class PeriodResolver {

    public PeriodRange resolveCurrent(Period period, LocalDate referenceDate, LocalDate customFrom, LocalDate customTo) {
        if (referenceDate == null) referenceDate = LocalDate.now();

        return switch (period) {
            case DAY -> new PeriodRange(referenceDate, referenceDate);
            case WEEK -> new PeriodRange(
                    referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    referenceDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            );
            case FORTNIGHT -> {
                if (referenceDate.getDayOfMonth() <= 15) {
                    yield new PeriodRange(referenceDate.withDayOfMonth(1), referenceDate.withDayOfMonth(15));
                } else {
                    yield new PeriodRange(referenceDate.withDayOfMonth(16), referenceDate.with(TemporalAdjusters.lastDayOfMonth()));
                }
            }
            case MONTH -> new PeriodRange(
                    referenceDate.with(TemporalAdjusters.firstDayOfMonth()),
                    referenceDate.with(TemporalAdjusters.lastDayOfMonth())
            );
            case QUARTER -> {
                int startMonth = ((referenceDate.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = referenceDate.withMonth(startMonth).with(TemporalAdjusters.firstDayOfMonth());
                yield new PeriodRange(start, start.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth()));
            }
            case SEMESTER -> {
                int startMonth = referenceDate.getMonthValue() <= 6 ? 1 : 7;
                LocalDate start = referenceDate.withMonth(startMonth).with(TemporalAdjusters.firstDayOfMonth());
                yield new PeriodRange(start, start.plusMonths(5).with(TemporalAdjusters.lastDayOfMonth()));
            }
            case YEAR -> new PeriodRange(
                    referenceDate.with(TemporalAdjusters.firstDayOfYear()),
                    referenceDate.with(TemporalAdjusters.lastDayOfYear())
            );
            case CUSTOM -> {
                if (customFrom == null || customTo == null || customFrom.isAfter(customTo)) {
                    throw new IllegalArgumentException("Datas inválidas para período CUSTOM");
                }
                long monthsBetween = ChronoUnit.MONTHS.between(customFrom, customTo);
                if (monthsBetween > 120) { // Limite expandido para 120 meses (10 anos)
                    throw new IllegalArgumentException("O período máximo para consulta é de 120 meses");
                }
                yield new PeriodRange(customFrom, customTo);
            }
        };
    }

    public PeriodRange resolvePrevious(Period period, PeriodRange current) {
        long days = ChronoUnit.DAYS.between(current.from(), current.to()) + 1;

        return switch (period) {
            case DAY -> new PeriodRange(current.from().minusDays(1), current.to().minusDays(1));
            case WEEK -> new PeriodRange(current.from().minusWeeks(1), current.to().minusWeeks(1));
            case FORTNIGHT -> {
                LocalDate previousDay = current.from().minusDays(1);
                if (previousDay.getDayOfMonth() == 15) {
                    yield new PeriodRange(previousDay.withDayOfMonth(1), previousDay);
                } else {
                    yield new PeriodRange(previousDay.withDayOfMonth(16), previousDay.with(TemporalAdjusters.lastDayOfMonth()));
                }
            }
            case MONTH -> new PeriodRange(
                    current.from().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()),
                    current.to().minusMonths(1).with(TemporalAdjusters.lastDayOfMonth())
            );
            case QUARTER -> new PeriodRange(
                    current.from().minusMonths(3).with(TemporalAdjusters.firstDayOfMonth()),
                    current.to().minusMonths(3).with(TemporalAdjusters.lastDayOfMonth())
            );
            case SEMESTER -> new PeriodRange(
                    current.from().minusMonths(6).with(TemporalAdjusters.firstDayOfMonth()),
                    current.to().minusMonths(6).with(TemporalAdjusters.lastDayOfMonth())
            );
            case YEAR -> new PeriodRange(
                    current.from().minusYears(1).with(TemporalAdjusters.firstDayOfYear()),
                    current.to().minusYears(1).with(TemporalAdjusters.lastDayOfYear())
            );
            case CUSTOM -> new PeriodRange(current.from().minusDays(days), current.to().minusDays(days));
        };
    }

    public Granularity resolveGranularity(PeriodRange range) {
        long days = ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
        if (days <= 31) return Granularity.DAY;
        if (days <= 100) return Granularity.WEEK;
        return Granularity.MONTH;
    }
}