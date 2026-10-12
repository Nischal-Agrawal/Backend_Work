package com.syfe.personalfinancemanager;

import com.syfe.personalfinancemanager.dto.request.GoalRequest;
import com.syfe.personalfinancemanager.dto.request.UpdateGoalRequest;
import com.syfe.personalfinancemanager.dto.response.ApiResponse;
import com.syfe.personalfinancemanager.dto.response.GoalListResponse;
import com.syfe.personalfinancemanager.dto.response.GoalResponse;
import com.syfe.personalfinancemanager.entity.SavingsGoal;
import com.syfe.personalfinancemanager.entity.Transaction;
import com.syfe.personalfinancemanager.entity.TransactionType;
import com.syfe.personalfinancemanager.entity.User;
import com.syfe.personalfinancemanager.exception.BadRequestException;
import com.syfe.personalfinancemanager.exception.ResourceNotFoundException;
import com.syfe.personalfinancemanager.repository.GoalRepository;
import com.syfe.personalfinancemanager.repository.TransactionRepository;
import com.syfe.personalfinancemanager.service.AuthService;
import com.syfe.personalfinancemanager.service.GoalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private GoalService goalService;

    @Test
    @DisplayName("Unit test: createGoal validation for blank name, non-positive amount, past target date")
    void testCreateGoalValidation() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);

        // Blank name
        assertThrows(BadRequestException.class, () -> goalService.createGoal(
                new GoalRequest("   ", BigDecimal.valueOf(1000), LocalDate.now().plusDays(10), LocalDate.now())));

        // Zero amount
        assertThrows(BadRequestException.class, () -> goalService.createGoal(
                new GoalRequest("Goal", BigDecimal.ZERO, LocalDate.now().plusDays(10), LocalDate.now())));

        // Past target date
        assertThrows(BadRequestException.class, () -> goalService.createGoal(
                new GoalRequest("Goal", BigDecimal.valueOf(1000), LocalDate.now().minusDays(1), LocalDate.now())));

        // targetDate before startDate
        assertThrows(BadRequestException.class, () -> goalService.createGoal(
                new GoalRequest("Goal", BigDecimal.valueOf(1000), LocalDate.now().plusDays(2), LocalDate.now().plusDays(5))));
    }

    @Test
    @DisplayName("Unit test: createGoal calculates progress and percentages accurately")
    void testCreateGoalSuccess() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);

        LocalDate startDate = LocalDate.now().minusMonths(1);
        LocalDate targetDate = LocalDate.now().plusMonths(6);
        SavingsGoal goal = new SavingsGoal("Car Fund", BigDecimal.valueOf(10000.00), targetDate, startDate, user);
        goal.setId(1L);

        when(goalRepository.save(any(SavingsGoal.class))).thenReturn(goal);

        // Transactions: Income 4000, Expense 1000 -> net progress = 3000
        Transaction t1 = new Transaction(BigDecimal.valueOf(4000), startDate, "Salary", null, TransactionType.INCOME, user);
        Transaction t2 = new Transaction(BigDecimal.valueOf(1000), startDate.plusDays(1), "Food", null, TransactionType.EXPENSE, user);
        when(transactionRepository.findByUserAndDateGreaterThanEqual(user, startDate)).thenReturn(List.of(t1, t2));

        GoalRequest req = new GoalRequest("Car Fund", BigDecimal.valueOf(10000.00), targetDate, startDate);
        GoalResponse resp = goalService.createGoal(req);

        assertEquals(1L, resp.getId());
        assertEquals("Car Fund", resp.getGoalName());
        assertEquals(BigDecimal.valueOf(3000.0).setScale(2), resp.getCurrentProgress());
        assertEquals(30.0, resp.getProgressPercentage());
        assertEquals(BigDecimal.valueOf(7000.0).setScale(2), resp.getRemainingAmount());
    }

    @Test
    @DisplayName("Unit test: getGoals returns all goals with metrics")
    void testGetGoals() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);

        SavingsGoal goal = new SavingsGoal("Goal1", BigDecimal.valueOf(2000.00), LocalDate.now().plusDays(30), LocalDate.now(), user);
        goal.setId(1L);
        when(goalRepository.findByUserOrderByIdAsc(user)).thenReturn(List.of(goal));
        when(transactionRepository.findByUserAndDateGreaterThanEqual(eq(user), any(LocalDate.class))).thenReturn(List.of());

        GoalListResponse resp = goalService.getGoals();
        assertEquals(1, resp.getGoals().size());
        assertEquals(0.0, resp.getGoals().get(0).getProgressPercentage());
    }

    @Test
    @DisplayName("Unit test: updateGoal and deleteGoal")
    void testUpdateAndDeleteGoal() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);

        SavingsGoal goal = new SavingsGoal("Goal1", BigDecimal.valueOf(2000.00), LocalDate.now().plusDays(30), LocalDate.now(), user);
        goal.setId(1L);
        when(goalRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(SavingsGoal.class))).thenAnswer(i -> i.getArgument(0));

        UpdateGoalRequest updateReq = new UpdateGoalRequest(BigDecimal.valueOf(2500.00), LocalDate.now().plusDays(40));
        GoalResponse updateResp = goalService.updateGoal(1L, updateReq);
        assertEquals(BigDecimal.valueOf(2500.00).setScale(2), updateResp.getTargetAmount());

        ApiResponse deleteResp = goalService.deleteGoal(1L);
        assertEquals("Goal deleted successfully", deleteResp.getMessage());
        verify(goalRepository, times(1)).delete(goal);
    }
}
