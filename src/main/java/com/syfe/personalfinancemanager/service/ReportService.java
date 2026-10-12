package com.syfe.personalfinancemanager.service;

import com.syfe.personalfinancemanager.dto.response.MonthlyReportResponse;
import com.syfe.personalfinancemanager.dto.response.YearlyReportResponse;
import com.syfe.personalfinancemanager.entity.Transaction;
import com.syfe.personalfinancemanager.entity.TransactionType;
import com.syfe.personalfinancemanager.entity.User;
import com.syfe.personalfinancemanager.exception.BadRequestException;
import com.syfe.personalfinancemanager.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Service generating monthly and yearly aggregation reports and financial analytics.
 */
@Service
public class ReportService {

    private final TransactionRepository transactionRepository;
    private final AuthService authService;

    public ReportService(TransactionRepository transactionRepository, AuthService authService) {
        this.transactionRepository = transactionRepository;
        this.authService = authService;
    }

    /**
     * Generates a monthly financial report aggregating income, expenses, and net savings.
     *
     * @param year Calendar year.
     * @param month Calendar month (1-12).
     * @return Monthly financial report.
     */
    @Transactional(readOnly = true)
    public MonthlyReportResponse getMonthlyReport(int year, int month) {
        if (year < 1900 || year > 2100) {
            throw new BadRequestException("Invalid year: " + year);
        }
        if (month < 1 || month > 12) {
            throw new BadRequestException("Invalid month: " + month + ". Month must be between 1 and 12.");
        }

        User currentUser = authService.getCurrentUser();
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = LocalDate.of(year, month, startDate.lengthOfMonth());

        List<Transaction> transactions = transactionRepository.findByUserAndDateBetween(currentUser, startDate, endDate);

        Map<String, BigDecimal> totalIncome = new TreeMap<>();
        Map<String, BigDecimal> totalExpenses = new TreeMap<>();
        BigDecimal sumIncome = BigDecimal.ZERO;
        BigDecimal sumExpenses = BigDecimal.ZERO;

        for (Transaction t : transactions) {
            BigDecimal amt = t.getAmount().setScale(2, RoundingMode.HALF_UP);
            if (t.getType() == TransactionType.INCOME) {
                totalIncome.put(t.getCategory(), totalIncome.getOrDefault(t.getCategory(), BigDecimal.ZERO).add(amt));
                sumIncome = sumIncome.add(amt);
            } else if (t.getType() == TransactionType.EXPENSE) {
                totalExpenses.put(t.getCategory(), totalExpenses.getOrDefault(t.getCategory(), BigDecimal.ZERO).add(amt));
                sumExpenses = sumExpenses.add(amt);
            }
        }

        BigDecimal netSavings = sumIncome.subtract(sumExpenses).setScale(2, RoundingMode.HALF_UP);

        return new MonthlyReportResponse(month, year, totalIncome, totalExpenses, netSavings);
    }

    /**
     * Generates a yearly financial report aggregating income, expenses, and net savings across the year.
     *
     * @param year Calendar year.
     * @return Yearly financial report.
     */
    @Transactional(readOnly = true)
    public YearlyReportResponse getYearlyReport(int year) {
        if (year < 1900 || year > 2100) {
            throw new BadRequestException("Invalid year: " + year);
        }

        User currentUser = authService.getCurrentUser();
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        List<Transaction> transactions = transactionRepository.findByUserAndDateBetween(currentUser, startDate, endDate);

        Map<String, BigDecimal> totalIncome = new TreeMap<>();
        Map<String, BigDecimal> totalExpenses = new TreeMap<>();
        BigDecimal sumIncome = BigDecimal.ZERO;
        BigDecimal sumExpenses = BigDecimal.ZERO;

        for (Transaction t : transactions) {
            BigDecimal amt = t.getAmount().setScale(2, RoundingMode.HALF_UP);
            if (t.getType() == TransactionType.INCOME) {
                totalIncome.put(t.getCategory(), totalIncome.getOrDefault(t.getCategory(), BigDecimal.ZERO).add(amt));
                sumIncome = sumIncome.add(amt);
            } else if (t.getType() == TransactionType.EXPENSE) {
                totalExpenses.put(t.getCategory(), totalExpenses.getOrDefault(t.getCategory(), BigDecimal.ZERO).add(amt));
                sumExpenses = sumExpenses.add(amt);
            }
        }

        BigDecimal netSavings = sumIncome.subtract(sumExpenses).setScale(2, RoundingMode.HALF_UP);

        return new YearlyReportResponse(year, totalIncome, totalExpenses, netSavings);
    }
}
