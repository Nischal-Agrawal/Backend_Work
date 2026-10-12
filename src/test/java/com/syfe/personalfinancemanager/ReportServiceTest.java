package com.syfe.personalfinancemanager;

import com.syfe.personalfinancemanager.dto.response.MonthlyReportResponse;
import com.syfe.personalfinancemanager.dto.response.YearlyReportResponse;
import com.syfe.personalfinancemanager.entity.Transaction;
import com.syfe.personalfinancemanager.entity.TransactionType;
import com.syfe.personalfinancemanager.entity.User;
import com.syfe.personalfinancemanager.exception.BadRequestException;
import com.syfe.personalfinancemanager.repository.TransactionRepository;
import com.syfe.personalfinancemanager.service.AuthService;
import com.syfe.personalfinancemanager.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private ReportService reportService;

    @Test
    @DisplayName("Unit test: getMonthlyReport validates month and year bounds")
    void testMonthlyReportValidation() {
        assertThrows(BadRequestException.class, () -> reportService.getMonthlyReport(2024, 0));
        assertThrows(BadRequestException.class, () -> reportService.getMonthlyReport(2024, 13));
        assertThrows(BadRequestException.class, () -> reportService.getMonthlyReport(1800, 5));
    }

    @Test
    @DisplayName("Unit test: getMonthlyReport aggregates income and expenses correctly")
    void testMonthlyReportCalculation() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);

        Transaction t1 = new Transaction(BigDecimal.valueOf(3000), LocalDate.of(2024, 1, 5), "Salary", null, TransactionType.INCOME, user);
        Transaction t2 = new Transaction(BigDecimal.valueOf(500), LocalDate.of(2024, 1, 10), "Freelance", null, TransactionType.INCOME, user);
        Transaction t3 = new Transaction(BigDecimal.valueOf(400), LocalDate.of(2024, 1, 12), "Food", null, TransactionType.EXPENSE, user);
        Transaction t4 = new Transaction(BigDecimal.valueOf(1200), LocalDate.of(2024, 1, 15), "Rent", null, TransactionType.EXPENSE, user);

        when(transactionRepository.findByUserAndDateBetween(eq(user), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(t1, t2, t3, t4));

        MonthlyReportResponse resp = reportService.getMonthlyReport(2024, 1);

        assertEquals(1, resp.getMonth());
        assertEquals(2024, resp.getYear());
        assertEquals(BigDecimal.valueOf(3000).setScale(2), resp.getTotalIncome().get("Salary"));
        assertEquals(BigDecimal.valueOf(500).setScale(2), resp.getTotalIncome().get("Freelance"));
        assertEquals(BigDecimal.valueOf(400).setScale(2), resp.getTotalExpenses().get("Food"));
        assertEquals(BigDecimal.valueOf(1200).setScale(2), resp.getTotalExpenses().get("Rent"));
        assertEquals(BigDecimal.valueOf(1900).setScale(2), resp.getNetSavings());
    }

    @Test
    @DisplayName("Unit test: getYearlyReport validates year and aggregates correctly")
    void testYearlyReportCalculation() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);

        Transaction t1 = new Transaction(BigDecimal.valueOf(36000), LocalDate.of(2024, 6, 1), "Salary", null, TransactionType.INCOME, user);
        Transaction t2 = new Transaction(BigDecimal.valueOf(4800), LocalDate.of(2024, 6, 1), "Food", null, TransactionType.EXPENSE, user);

        when(transactionRepository.findByUserAndDateBetween(eq(user), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(t1, t2));

        YearlyReportResponse resp = reportService.getYearlyReport(2024);

        assertEquals(2024, resp.getYear());
        assertEquals(BigDecimal.valueOf(36000).setScale(2), resp.getTotalIncome().get("Salary"));
        assertEquals(BigDecimal.valueOf(4800).setScale(2), resp.getTotalExpenses().get("Food"));
        assertEquals(BigDecimal.valueOf(31200).setScale(2), resp.getNetSavings());
    }
}
