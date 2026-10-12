package com.syfe.personalfinancemanager.exception;

/**
 * Exception thrown when a conflict occurs (e.g. duplicate username, duplicate category, referenced entity deletion).
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
