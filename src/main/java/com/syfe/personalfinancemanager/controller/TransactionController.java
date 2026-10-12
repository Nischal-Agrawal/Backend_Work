package com.syfe.personalfinancemanager.controller;

import com.syfe.personalfinancemanager.dto.request.TransactionRequest;
import com.syfe.personalfinancemanager.dto.request.UpdateTransactionRequest;
import com.syfe.personalfinancemanager.dto.response.ApiResponse;
import com.syfe.personalfinancemanager.dto.response.TransactionListResponse;
import com.syfe.personalfinancemanager.dto.response.TransactionResponse;
import com.syfe.personalfinancemanager.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Controller handling transaction CRUD and filtering operations.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Creates a new financial transaction.
     *
     * @param request Transaction creation request.
     * @return 201 Created with created transaction details.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = transactionService.createTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves transactions for the current user matching optional filter criteria.
     *
     * @param startDate Optional start date filter (inclusive).
     * @param endDate Optional end date filter (inclusive).
     * @param categoryId Optional category ID filter.
     * @param category Optional category name filter.
     * @param type Optional transaction type (INCOME or EXPENSE).
     * @return 200 OK with list of transactions.
     */
    @GetMapping
    public ResponseEntity<TransactionListResponse> getTransactions(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "type", required = false) String type) {

        TransactionListResponse response = transactionService.getTransactions(startDate, endDate, categoryId, category, type);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing transaction.
     *
     * @param id Transaction ID.
     * @param request Update request.
     * @return 200 OK with updated transaction.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable("id") Long id,
            @RequestBody UpdateTransactionRequest request) {

        TransactionResponse response = transactionService.updateTransaction(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a transaction by ID.
     *
     * @param id Transaction ID.
     * @return 200 OK with success confirmation.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteTransaction(@PathVariable("id") Long id) {
        ApiResponse response = transactionService.deleteTransaction(id);
        return ResponseEntity.ok(response);
    }
}
