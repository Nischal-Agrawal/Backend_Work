package com.syfe.personalfinancemanager.exception;

/**
 * Exception thrown when an authenticated user attempts an operation forbidden by policy.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
