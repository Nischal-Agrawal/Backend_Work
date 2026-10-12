package com.syfe.personalfinancemanager;

import com.syfe.personalfinancemanager.dto.request.TransactionRequest;
import com.syfe.personalfinancemanager.dto.request.UpdateTransactionRequest;
import com.syfe.personalfinancemanager.dto.response.ApiResponse;
import com.syfe.personalfinancemanager.dto.response.TransactionListResponse;
import com.syfe.personalfinancemanager.dto.response.TransactionResponse;
import com.syfe.personalfinancemanager.entity.Category;
import com.syfe.personalfinancemanager.entity.Transaction;
import com.syfe.personalfinancemanager.entity.TransactionType;
import com.syfe.personalfinancemanager.entity.User;
import com.syfe.personalfinancemanager.exception.BadRequestException;
import com.syfe.personalfinancemanager.exception.ResourceNotFoundException;
import com.syfe.personalfinancemanager.repository.CategoryRepository;
import com.syfe.personalfinancemanager.repository.TransactionRepository;
import com.syfe.personalfinancemanager.service.AuthService;
import com.syfe.personalfinancemanager.service.CategoryService;
import com.syfe.personalfinancemanager.service.TransactionService;
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
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    @DisplayName("Unit test: createTransaction validates amount and future date")
    void testCreateTransactionValidation() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);

        // Negative amount
        TransactionRequest req1 = new TransactionRequest(BigDecimal.valueOf(-10), LocalDate.now(), "Salary", "Test");
        assertThrows(BadRequestException.class, () -> transactionService.createTransaction(req1));

        // Future date
        TransactionRequest req2 = new TransactionRequest(BigDecimal.valueOf(100), LocalDate.now().plusDays(1), "Salary", "Test");
        assertThrows(BadRequestException.class, () -> transactionService.createTransaction(req2));
    }

    @Test
    @DisplayName("Unit test: createTransaction succeeds and saves transaction")
    void testCreateTransactionSuccess() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);

        Category cat = new Category("Salary", TransactionType.INCOME, false, null);
        when(categoryService.validateAndGetAccessibleCategory(user, "Salary")).thenReturn(cat);

        Transaction tx = new Transaction(BigDecimal.valueOf(5000), LocalDate.now(), "Salary", "Pay", TransactionType.INCOME, user);
        tx.setId(101L);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(tx);

        TransactionRequest req = new TransactionRequest(BigDecimal.valueOf(5000), LocalDate.now(), "Salary", "Pay");
        TransactionResponse resp = transactionService.createTransaction(req);

        assertEquals(101L, resp.getId());
        assertEquals("INCOME", resp.getType());
        assertEquals(BigDecimal.valueOf(5000), resp.getAmount());
    }

    @Test
    @DisplayName("Unit test: getTransactions validates date range and type")
    void testGetTransactionsValidation() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);

        // startDate after endDate
        assertThrows(BadRequestException.class, () -> transactionService.getTransactions(
                LocalDate.of(2024, 2, 1), LocalDate.of(2024, 1, 1), null, null, null));

        // Invalid type
        assertThrows(BadRequestException.class, () -> transactionService.getTransactions(
                null, null, null, null, "INVALID"));
    }

    @Test
    @DisplayName("Unit test: updateTransaction forbids date change")
    void testUpdateTransactionImmutableDate() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);

        Transaction tx = new Transaction(BigDecimal.valueOf(5000), LocalDate.of(2024, 1, 10), "Salary", "Pay", TransactionType.INCOME, user);
        when(transactionRepository.findByIdAndUser(101L, user)).thenReturn(Optional.of(tx));

        UpdateTransactionRequest req = new UpdateTransactionRequest();
        req.setDate(LocalDate.of(2024, 1, 20));

        assertThrows(BadRequestException.class, () -> transactionService.updateTransaction(101L, req));
    }

    @Test
    @DisplayName("Unit test: updateTransaction succeeds with valid updates")
    void testUpdateTransactionSuccess() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);

        Transaction tx = new Transaction(BigDecimal.valueOf(5000), LocalDate.of(2024, 1, 10), "Salary", "Pay", TransactionType.INCOME, user);
        when(transactionRepository.findByIdAndUser(101L, user)).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        UpdateTransactionRequest req = new UpdateTransactionRequest();
        req.setAmount(BigDecimal.valueOf(6000));
        req.setDescription("Updated desc");

        TransactionResponse resp = transactionService.updateTransaction(101L, req);
        assertEquals(BigDecimal.valueOf(6000), resp.getAmount());
        assertEquals("Updated desc", resp.getDescription());
    }

    @Test
    @DisplayName("Unit test: deleteTransaction succeeds when owned")
    void testDeleteTransactionSuccess() {
        User user = new User("user@test.com", "pass", "User", "+123");
        when(authService.getCurrentUser()).thenReturn(user);

        Transaction tx = new Transaction(BigDecimal.valueOf(5000), LocalDate.now(), "Salary", "Pay", TransactionType.INCOME, user);
        when(transactionRepository.findByIdAndUser(101L, user)).thenReturn(Optional.of(tx));

        ApiResponse resp = transactionService.deleteTransaction(101L);
        assertEquals("Transaction deleted successfully", resp.getMessage());
        verify(transactionRepository, times(1)).delete(tx);
    }
}
