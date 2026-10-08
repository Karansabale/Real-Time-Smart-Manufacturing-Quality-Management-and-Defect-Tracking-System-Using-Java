package com.project.qms.exception;

/**
 * Thrown when something was asked for by an id or reference that does not
 * exist - for example a defect reference typed into the search box.
 * GlobalExceptionHandler turns this into a 404 with a clear message.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    /** The everyday case: "Product 42 was not found." */
    public static ResourceNotFoundException of(String what, Object id) {
        return new ResourceNotFoundException(what + " not found: " + id);
    }
}
