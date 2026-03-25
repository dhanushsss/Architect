package com.architect.github;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks last GitHub API response headers for rate-limit backoff and zero-remaining handling.
 */
@Slf4j
@Component
public class GitHubRateLimitMonitor {

    /** Only pause when dangerously low — avoid adding ~100ms per file when remaining is merely &lt;100. */
    private static final int THROTTLE_THRESHOLD = 25;
    private static final int THROTTLE_DELAY_MS = 75;

    private final AtomicInteger lastRemaining = new AtomicInteger(-1);
    private final AtomicLong lastResetEpochSeconds = new AtomicLong(0);

    public void recordFromHeaders(String remainingHeader, String resetHeader) {
        if (remainingHeader != null) {
            try {
                lastRemaining.set(Integer.parseInt(remainingHeader.trim()));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        if (resetHeader != null) {
            try {
                lastResetEpochSeconds.set(Long.parseLong(resetHeader.trim()));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        int rem = lastRemaining.get();
        if (rem >= 0 && rem < THROTTLE_THRESHOLD) {
            log.warn("GitHub API rate limit low: {} remaining (threshold {})", rem, THROTTLE_THRESHOLD);
        }
    }

    /**
     * Call between sequential scanner requests when remaining is low.
     */
    public void maybeThrottleAfterRequest() throws InterruptedException {
        int rem = lastRemaining.get();
        if (rem >= 0 && rem < THROTTLE_THRESHOLD) {
            Thread.sleep(THROTTLE_DELAY_MS);
        }
    }

    public int getLastRemaining() {
        return lastRemaining.get();
    }

    public Instant getLastResetInstant() {
        long s = lastResetEpochSeconds.get();
        return s > 0 ? Instant.ofEpochSecond(s) : Instant.now().plusSeconds(3600);
    }

    public void checkRemainingAfterResponse() {
        if (lastRemaining.get() == 0) {
            throw new RateLimitExceededException(getLastResetInstant());
        }
    }
}
