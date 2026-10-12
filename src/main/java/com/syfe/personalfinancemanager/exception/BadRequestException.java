package com.syfe.personalfinancemanager.exception;

/**
 * Exception thrown when a request is invalid or violates validation rules.
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
