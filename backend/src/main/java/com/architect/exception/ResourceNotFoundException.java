package com.architect.exception;

/**
 * Thrown when a requested resource (repo, endpoint, etc.) does not exist
 * or does not belong to the authenticated user.
 * Maps to HTTP 404 in {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceType, Long id) {
        super(resourceType + " with id " + id + " not found");
    }

    public ResourceNotFoundException(String resourceType, String identifier) {
        super(resourceType + " '" + identifier + "' not found");
    }
}
