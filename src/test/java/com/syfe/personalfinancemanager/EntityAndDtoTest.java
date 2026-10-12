package com.syfe.personalfinancemanager;

import com.syfe.personalfinancemanager.dto.request.*;
import com.syfe.personalfinancemanager.dto.response.*;
import com.syfe.personalfinancemanager.entity.*;
import com.syfe.personalfinancemanager.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EntityAndDtoTest {

    @Test
    @DisplayName("Unit test: User entity getters and setters")
    void testUserEntity() {
        User user = new User();
        user.setId(1L);
        user.setUsername("u@e.com");
        user.setPassword("pass");
        user.setFullName("Name");
        user.setPhoneNumber("+12345");
        user.setCreatedAt(LocalDateTime.now());
        user.prePersist();

        assertEquals(1L, user.getId());
        assertEquals("u@e.com", user.getUsername());
        assertEquals("pass", user.getPassword());
        assertEquals("Name", user.getFullName());
        assertEquals("+12345", user.getPhoneNumber());
        assertNotNull(user.getCreatedAt());
    }

    @Test
    @DisplayName("Unit test: Category entity getters and setters")
    void testCategoryEntity() {
        User user = new User();
        Category cat = new Category();
        cat.setId(2L);
        cat.setName("TestCat");
        cat.setType(TransactionType.EXPENSE);
        cat.setCustom(true);
        cat.setUser(user);

        assertEquals(2L, cat.getId());
        assertEquals("TestCat", cat.getName());
        assertEquals(TransactionType.EXPENSE, cat.getType());
        assertTrue(cat.isCustom());
        assertEquals(user, cat.getUser());
    }

    @Test
    @DisplayName("Unit test: Transaction entity getters and setters")
    void testTransactionEntity() {
        User user = new User();
        Transaction tx = new Transaction();
        tx.setId(3L);
        tx.setAmount(BigDecimal.valueOf(100));
        tx.setDate(LocalDate.now());
        tx.setCategory("Food");
        tx.setDescription("Groceries");
        tx.setType(TransactionType.EXPENSE);
        tx.setUser(user);
        tx.setCreatedAt(LocalDateTime.now());
        tx.prePersist();

        assertEquals(3L, tx.getId());
        assertEquals(BigDecimal.valueOf(100), tx.getAmount());
        assertNotNull(tx.getDate());
        assertEquals("Food", tx.getCategory());
        assertEquals("Groceries", tx.getDescription());
        assertEquals(TransactionType.EXPENSE, tx.getType());
        assertEquals(user, tx.getUser());
        assertNotNull(tx.getCreatedAt());
    }

    @Test
    @DisplayName("Unit test: SavingsGoal entity getters and setters")
    void testSavingsGoalEntity() {
        User user = new User();
        SavingsGoal g = new SavingsGoal();
        g.setId(4L);
        g.setGoalName("Vacation");
        g.setTargetAmount(BigDecimal.valueOf(5000));
        g.setTargetDate(LocalDate.now().plusMonths(6));
        g.setStartDate(LocalDate.now());
        g.setUser(user);
        g.setCreatedAt(LocalDateTime.now());
        g.prePersist();

        assertEquals(4L, g.getId());
        assertEquals("Vacation", g.getGoalName());
        assertEquals(BigDecimal.valueOf(5000), g.getTargetAmount());
        assertNotNull(g.getTargetDate());
        assertNotNull(g.getStartDate());
        assertEquals(user, g.getUser());
        assertNotNull(g.getCreatedAt());
    }

    @Test
    @DisplayName("Unit test: DTOs constructors and properties")
    void testDtoProperties() {
        ApiResponse apiResp = new ApiResponse();
        apiResp.setMessage("msg");
        assertEquals("msg", apiResp.getMessage());

        RegisterResponse regResp = new RegisterResponse();
        regResp.setMessage("ok");
        regResp.setUserId(9L);
        assertEquals("ok", regResp.getMessage());
        assertEquals(9L, regResp.getUserId());

        CategoryListResponse clr = new CategoryListResponse();
        clr.setCategories(List.of(new CategoryResponse("C", "INCOME", false)));
        assertEquals(1, clr.getCategories().size());

        TransactionListResponse tlr = new TransactionListResponse();
        tlr.setTransactions(Collections.emptyList());
        assertTrue(tlr.getTransactions().isEmpty());

        GoalListResponse glr = new GoalListResponse();
        glr.setGoals(Collections.emptyList());
        assertTrue(glr.getGoals().isEmpty());

        ErrorResponse err = new ErrorResponse();
        err.setError("Err");
        err.setMessage("Detail");
        err.setStatus(400);
        err.setTimestamp(LocalDateTime.now());
        assertEquals("Err", err.getError());
        assertEquals("Detail", err.getMessage());
        assertEquals(400, err.getStatus());
        assertNotNull(err.getTimestamp());

        MonthlyReportResponse mrr = new MonthlyReportResponse();
        mrr.setMonth(5);
        mrr.setYear(2024);
        mrr.setTotalIncome(Map.of());
        mrr.setTotalExpenses(Map.of());
        mrr.setNetSavings(BigDecimal.ZERO);
        assertEquals(5, mrr.getMonth());
        assertEquals(2024, mrr.getYear());

        YearlyReportResponse yrr = new YearlyReportResponse();
        yrr.setYear(2024);
        yrr.setTotalIncome(Map.of());
        yrr.setTotalExpenses(Map.of());
        yrr.setNetSavings(BigDecimal.ZERO);
        assertEquals(2024, yrr.getYear());
    }

    @Test
    @DisplayName("Unit test: GlobalExceptionHandler edge cases")
    void testGlobalExceptionHandler() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var accessDenied = handler.handleAccessDenied(new AccessDeniedException("Forbidden"));
        assertEquals(403, accessDenied.getStatusCode().value());

        var dataIntegrity = handler.handleDataIntegrity(new DataIntegrityViolationException("Conflict"));
        assertEquals(409, dataIntegrity.getStatusCode().value());

        var generic = handler.handleGenericException(new RuntimeException("Crash"));
        assertEquals(500, generic.getStatusCode().value());
    }
}
