package com.architect.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;

/**
 * Centralised exception → HTTP response mapper for all {@code /api/v1/**} controllers.
 *
 * <p>Before this existed, unhandled exceptions bubbled up as either:
 * <ul>
 *   <li>500 with a Spring whitepage (leaks internal stack traces)</li>
 *   <li>403 from Spring Security swallowing a {@code LazyInitializationException}</li>
 * </ul>
 *
 * <p>Now every error returns a typed {@link ApiError} JSON with a stable {@code code}
 * field — clients can switch on {@code code} instead of parsing free-text messages.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Class<?> required = ex.getRequiredType();
        return error(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH",
                "Parameter '" + ex.getName() + "' must be of type "
                        + (required != null ? required.getSimpleName() : "unknown"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNoHandler(NoHandlerFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND",
                "No endpoint: " + ex.getHttpMethod() + " " + ex.getRequestURL());
    }

    /** Catch-all — log the full stack trace but hide internals from the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again.");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), code, message, Instant.now().toString()));
    }
}
