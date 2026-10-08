package com.project.qms.exception;

/**
 * Thrown when a value that must be unique is already taken - a duplicate
 * username (FR-02.3) or product code (FR-03.3).
 *
 * It is a separate type from BusinessRuleException because it becomes a
 * different HTTP status: 409 Conflict, meaning "your request was fine, but it
 * clashes with something that already exists".
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
