package com.project.qms.exception;

/**
 * Thrown when a request is well-formed but breaks a rule of the business -
 * moving a defect straight from OPEN to CLOSED, verifying an action that has
 * not been completed, or deleting a product that has batches.
 *
 * These are NOT programming errors. They are the system doing its job, and
 * they become a 400 with the message written for the user to read.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
