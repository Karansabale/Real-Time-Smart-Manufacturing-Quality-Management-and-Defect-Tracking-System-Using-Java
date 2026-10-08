package com.project.qms.controller;

import com.project.qms.dto.ApiError;
import com.project.qms.exception.BusinessRuleException;
import com.project.qms.exception.DuplicateResourceException;
import com.project.qms.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Turns every exception into one consistent JSON error shape (FR-12.8).
 *
 * WHY ONE CLASS INSTEAD OF try/catch EVERYWHERE: with a try/catch in every
 * controller method, one method would eventually forget, and that method would
 * return a Java stack trace to the browser - which leaks class names, file
 * paths and library versions (FR-12.9). Here it is handled once and cannot be
 * forgotten.
 *
 * The four outcomes, and the HTTP status each one earns:
 *
 *   404  ResourceNotFoundException  - it does not exist
 *   409  DuplicateResourceException - it clashes with something that does
 *   400  BusinessRuleException      - your request broke a rule of the business
 *   400  validation failure         - a field is missing or the wrong length
 *   500  anything else              - our fault, and the user is told only that
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), List.of());
    }

    /**
     * A database integrity rule refused the change - for example deleting a
     * product that an inspection still refers to. The specific causes are
     * checked in the services and give a friendlier message, but this handler
     * makes sure that ANY such case answers 400 with an explanation instead of
     * falling through to the catch-all and telling the user the server broke.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Integrity refused: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "That change would break a link to another record, so it was refused. "
                + "Deal with the records that refer to this one first.", List.of());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), List.of());
    }

    /**
     * A business rule was broken. Not an error in the code - the system doing
     * its job. The message is written for the person using the screen, so it is
     * passed straight through.
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex) {
        // NFR-02.2: a refused action is refused AND logged. The line records
        // what was refused and why, which is what an audit trail needs and what
        // makes a "why did it say no?" question answerable from the log file.
        log.warn("Refused: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    /** Field-level validation: collects every problem, not just the first. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describeFieldError)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.", details);
    }

    /**
     * A path or query value that does not fit its type - for example
     * /api/defects/abc when the id must be a number. Without this handler the
     * request falls through to the catch-all below and the user is told the
     * server broke, which is both wrong and unhelpful.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST,
                "The value '" + ex.getValue() + "' is not valid for '" + ex.getName() + "'.", List.of());
    }

    /** A request body that is missing, malformed, or not valid JSON. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST,
                "The request body is missing or is not valid JSON.", List.of());
    }

    /**
     * An address that matches neither a controller nor a page. Spring reports
     * this as a missing static resource, which the catch-all below would turn
     * into a 500 - telling the user the server broke when in fact the address
     * was simply wrong.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "No endpoint or page matches that address.", List.of());
    }

    /**
     * An address that exists, called with a method it does not support - a POST
     * where only GET belongs, say. Spring already knows which methods the
     * address accepts; without this handler the catch-all below answered 500
     * and told the caller the server had broken, when in fact the request was
     * wrong (found in Phase 10, D-02).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleWrongMethod(HttpRequestMethodNotSupportedException ex) {
        log.warn("Refused: method {} is not supported on this address. Allowed: {}",
                 ex.getMethod(), ex.getSupportedHttpMethods());
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "That address does not accept " + ex.getMethod() + " requests.",
                List.of("Allowed: " + ex.getSupportedHttpMethods()));
    }

    /** Anything unplanned. The trace is logged, never sent to the browser. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on the server. The problem has been logged.", List.of());
    }

    private String describeFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(new ApiError(
                status.value(),
                status.getReasonPhrase(),
                message,
                details,
                LocalDateTime.now()));
    }
}
