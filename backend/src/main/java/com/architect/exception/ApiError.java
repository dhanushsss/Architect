package com.architect.exception;

/**
 * Uniform error envelope returned by {@link GlobalExceptionHandler} for every
 * non-2xx response from versioned API endpoints.
 *
 * <pre>
 * {
 *   "status":  400,
 *   "code":    "MISSING_PARAMETER",
 *   "message": "Required parameter 'repoId' is missing",
 *   "timestamp": "2026-03-24T10:15:30Z"
 * }
 * </pre>
 *
 * Using a Java {@code record} makes this immutable and serialisable by Jackson
 * with zero boilerplate.
 */
public record ApiError(int status, String code, String message, String timestamp) {}
