package com.architect.github;

import java.time.Instant;

public class RateLimitExceededException extends RuntimeException {

    private final Instant resetAt;

    public RateLimitExceededException(Instant resetAt) {
        super("GitHub API rate limit exceeded; reset at " + resetAt);
        this.resetAt = resetAt;
    }

    public Instant getResetAt() {
        return resetAt;
    }
}
