package com.syfe.personalfinancemanager.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service managing financial transactions including CRUD, filtering, and validation.
 */
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final AuthService authService;

    public TransactionService(TransactionRepository transactionRepository,
                              CategoryRepository categoryRepository,
                              CategoryService categoryService,
                              AuthService authService) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
        this.authService = authService;
    }

    /**
     * Creates a new financial transaction for the authenticated user.
     *
     * @param request Transaction creation payload.
     * @return Created transaction response.
     */
    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request) {
        User currentUser = authService.getCurrentUser();

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }

        if (request.getDate() == null) {
            throw new BadRequestException("Date is required");
        }

        if (request.getDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("Transaction date cannot be in the future");
        }

        Category category = categoryService.validateAndGetAccessibleCategory(currentUser, request.getCategory());

        Transaction transaction = new Transaction(
                request.getAmount(),
                request.getDate(),
                category.getName(),
                request.getDescription(),
                category.getType(),
                currentUser
        );

        Transaction saved = transactionRepository.save(transaction);
        return toResponse(saved);
    }

    /**
     * Retrieves transactions for the current user matching optional filter criteria.
     *
     * @param startDate Optional inclusive start date.
     * @param endDate Optional inclusive end date.
     * @param categoryId Optional category ID filter.
     * @param categoryName Optional category name filter.
     * @param typeStr Optional transaction type filter.
     * @return Filtered list of transactions.
     */
    @Transactional(readOnly = true)
    public TransactionListResponse getTransactions(LocalDate startDate,
                                                   LocalDate endDate,
                                                   Long categoryId,
                                                   String categoryName,
                                                   String typeStr) {
        User currentUser = authService.getCurrentUser();

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BadRequestException("startDate cannot be after endDate");
        }

        TransactionType type = null;
        if (typeStr != null && !typeStr.isBlank()) {
            try {
                type = TransactionType.valueOf(typeStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid transaction type: " + typeStr);
            }
        }

        String filterCategory = categoryName != null && !categoryName.isBlank() ? categoryName.trim() : null;
        if (categoryId != null) {
            Category cat = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new BadRequestException("Category with ID " + categoryId + " not found"));
            // Verify category is accessible to this user
            if (cat.getUser() != null && !cat.getUser().getId().equals(currentUser.getId())) {
                throw new BadRequestException("Inaccessible category ID: " + categoryId);
            }
            filterCategory = cat.getName();
        }

        List<Transaction> transactions = transactionRepository.findFilteredTransactions(
                currentUser,
                startDate,
                endDate,
                filterCategory,
                type
        );

        List<TransactionResponse> responseList = transactions.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return new TransactionListResponse(responseList);
    }

    /**
     * Updates an existing transaction.
     *
     * @param id Transaction ID.
     * @param request Update payload.
     * @return Updated transaction response.
     */
    @Transactional
    public TransactionResponse updateTransaction(Long id, UpdateTransactionRequest request) {
        User currentUser = authService.getCurrentUser();
        Transaction transaction = transactionRepository.findByIdAndUser(id, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));

        // Date field is immutable
        if (request.getDate() != null && !request.getDate().equals(transaction.getDate())) {
            throw new BadRequestException("Transaction date cannot be modified");
        }

        if (request.getAmount() != null) {
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Amount must be greater than zero");
            }
            transaction.setAmount(request.getAmount());
        }

        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            Category category = categoryService.validateAndGetAccessibleCategory(currentUser, request.getCategory());
            transaction.setCategory(category.getName());
            transaction.setType(category.getType());
        }

        if (request.getDescription() != null) {
            transaction.setDescription(request.getDescription());
        }

        Transaction updated = transactionRepository.save(transaction);
        return toResponse(updated);
    }

    /**
     * Deletes a transaction owned by the authenticated user.
     *
     * @param id Transaction ID.
     * @return Success API response.
     */
    @Transactional
    public ApiResponse deleteTransaction(Long id) {
        User currentUser = authService.getCurrentUser();
        Transaction transaction = transactionRepository.findByIdAndUser(id, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));

        transactionRepository.delete(transaction);
        return new ApiResponse("Transaction deleted successfully");
    }

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getAmount(),
                t.getDate(),
                t.getCategory(),
                t.getDescription(),
                t.getType().name()
        );
    }
}
