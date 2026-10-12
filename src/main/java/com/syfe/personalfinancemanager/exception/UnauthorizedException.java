package com.syfe.personalfinancemanager.exception;

/**
 * Exception thrown when authentication fails or is required.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
