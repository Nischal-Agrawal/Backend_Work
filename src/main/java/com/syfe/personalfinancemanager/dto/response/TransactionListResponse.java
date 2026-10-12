package com.syfe.personalfinancemanager.dto.response;

import java.util.List;

/**
 * Response DTO wrapper for list of transactions.
 */
public class TransactionListResponse {

    private List<TransactionResponse> transactions;

    public TransactionListResponse() {
    }

    public TransactionListResponse(List<TransactionResponse> transactions) {
        this.transactions = transactions;
    }

    public List<TransactionResponse> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<TransactionResponse> transactions) {
        this.transactions = transactions;
    }
}
