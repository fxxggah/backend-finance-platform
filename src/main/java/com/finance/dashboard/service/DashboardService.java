package com.finance.dashboard.service;

import com.finance.common.enums.Granularity;
import com.finance.common.enums.Period;
import com.finance.common.enums.TransactionType;
import com.finance.dashboard.dto.*;
import com.finance.transaction.entity.Transaction;
import com.finance.transaction.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final TransactionRepository transactionRepository;
    private final PeriodResolver periodResolver;

    public DashboardService(TransactionRepository transactionRepository, PeriodResolver periodResolver) {
        this.transactionRepository = transactionRepository;
        this.periodResolver = periodResolver;
    }

    public DashboardSummaryDTO getSummary(Long userId, Period period, LocalDate date, LocalDate from, LocalDate to, Long accountId, Long creditCardId) {
        PeriodRange current = periodResolver.resolveCurrent(period, date, from, to);
        PeriodRange prev = periodResolver.resolvePrevious(period, current);

        BigDecimal currentIncome = getSum(userId, TransactionType.INCOME, current, accountId, creditCardId);
        BigDecimal currentExpense = getSum(userId, TransactionType.EXPENSE, current, accountId, creditCardId);
        BigDecimal currentBalance = currentIncome.subtract(currentExpense);

        BigDecimal prevIncome = getSum(userId, TransactionType.INCOME, prev, accountId, creditCardId);
        BigDecimal prevExpense = getSum(userId, TransactionType.EXPENSE, prev, accountId, creditCardId);

        BigDecimal incomeChange = calculatePercentageChange(prevIncome, currentIncome);
        BigDecimal expenseChange = calculatePercentageChange(prevExpense, currentExpense);

        PreviousSummaryDTO previousDto = new PreviousSummaryDTO(prev.from(), prev.to(), incomeChange, expenseChange);

        return new DashboardSummaryDTO(period, current.from(), current.to(), currentIncome, currentExpense, currentBalance, previousDto);
    }

    public List<CategoryExpenseDTO> getExpensesByCategory(Long userId, Period period, LocalDate date, LocalDate from, LocalDate to, Long accountId, Long creditCardId) {
        PeriodRange range = periodResolver.resolveCurrent(period, date, from, to);
        List<CategoryExpenseDTO> expenses = transactionRepository.findExpensesByCategory(userId, range.from(), range.to(), accountId, creditCardId);

        BigDecimal totalExpense = expenses.stream()
                .map(CategoryExpenseDTO::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalExpense.compareTo(BigDecimal.ZERO) == 0) return expenses;

        return expenses.stream()
                .map(e -> new CategoryExpenseDTO(
                        e.categoryId(), e.name(), e.color(), e.icon(), e.total(),
                        e.total().multiply(new BigDecimal("100")).divide(totalExpense, 2, RoundingMode.HALF_EVEN),
                        e.count()))
                .toList();
    }

    public EvolutionDTO getEvolution(Long userId, Period period, LocalDate date, LocalDate from, LocalDate to, Long accountId, Long creditCardId) {
        PeriodRange range = periodResolver.resolveCurrent(period, date, from, to);
        Granularity granularity = periodResolver.resolveGranularity(range);

        List<Object[]> rawData = transactionRepository.findEvolutionData(userId, range.from(), range.to(), accountId, creditCardId);

        Map<LocalDate, BigDecimal> incomeMap = new HashMap<>();
        Map<LocalDate, BigDecimal> expenseMap = new HashMap<>();

        for (Object[] row : rawData) {
            LocalDate rowDate = (LocalDate) row[0];
            TransactionType type = (TransactionType) row[1];
            BigDecimal amount = (BigDecimal) row[2];
            if (type == TransactionType.INCOME) incomeMap.put(rowDate, amount);
            else expenseMap.put(rowDate, amount);
        }

        List<EvolutionPointDTO> points = buildEvolutionBuckets(range, granularity, incomeMap, expenseMap);
        return new EvolutionDTO(granularity, points);
    }

    public List<TransactionRecentDTO> getRecentTransactions(Long userId, int limit) {
        return transactionRepository.findAllByUserIdAndDeletedFalseWithFetch(
                userId, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "date", "createdAt"))
        ).getContent().stream().map(t -> new TransactionRecentDTO(
                t.getId(),
                t.getAmount(),
                t.getDate(),
                t.getType(),
                t.getCategory() != null ? t.getCategory().getName() : null,
                t.getAccount() != null ? t.getAccount().getName() : null,
                t.getCreditCard() != null ? t.getCreditCard().getName() : null
        )).toList();
    }

    private BigDecimal getSum(Long userId, TransactionType type, PeriodRange range, Long accountId, Long creditCardId) {
        return transactionRepository.sumAmountByTypeAndPeriodAndFilters(userId, type, range.from(), range.to(), accountId, creditCardId);
    }

    private BigDecimal calculatePercentageChange(BigDecimal previous, BigDecimal current) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) return null; // Previne divisão por zero[cite: 7]
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_EVEN)
                .multiply(new BigDecimal("100"));
    }

    private List<EvolutionPointDTO> buildEvolutionBuckets(PeriodRange range, Granularity granularity,
                                                          Map<LocalDate, BigDecimal> incomeMap,
                                                          Map<LocalDate, BigDecimal> expenseMap) {
        List<EvolutionPointDTO> points = new ArrayList<>();
        LocalDate iter = range.from();

        while (!iter.isAfter(range.to())) {
            LocalDate bucketEnd;
            String label;

            if (granularity == Granularity.DAY) {
                bucketEnd = iter;
                label = String.format("%02d/%02d", iter.getDayOfMonth(), iter.getMonthValue());
            } else if (granularity == Granularity.WEEK) {
                bucketEnd = iter.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                if (bucketEnd.isAfter(range.to())) bucketEnd = range.to();
                label = String.format("%02d/%02d - %02d/%02d", iter.getDayOfMonth(), iter.getMonthValue(), bucketEnd.getDayOfMonth(), bucketEnd.getMonthValue());
            } else {
                bucketEnd = iter.with(TemporalAdjusters.lastDayOfMonth());
                if (bucketEnd.isAfter(range.to())) bucketEnd = range.to();
                label = String.format("%02d/%d", iter.getMonthValue(), iter.getYear());
            }

            BigDecimal inc = BigDecimal.ZERO;
            BigDecimal exp = BigDecimal.ZERO;

            for (LocalDate d = iter; !d.isAfter(bucketEnd); d = d.plusDays(1)) {
                inc = inc.add(incomeMap.getOrDefault(d, BigDecimal.ZERO));
                exp = exp.add(expenseMap.getOrDefault(d, BigDecimal.ZERO));
            }

            points.add(new EvolutionPointDTO(label, iter, bucketEnd, inc, exp, inc.subtract(exp)));
            iter = bucketEnd.plusDays(1);
        }
        return points;
    }
}