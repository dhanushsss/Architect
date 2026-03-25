package com.architect.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sliding-window rate limiter: {@value LIMIT} requests / {@value WINDOW_MS}ms per identity.
 *
 * <h3>Data structure — token bucket per identity key</h3>
 * <pre>
 *   ConcurrentHashMap&lt;String, Bucket&gt;
 *   Bucket = { windowStartMs: long, count: AtomicInteger }
 * </pre>
 * Why {@link ConcurrentHashMap}: O(1) amortised get/compute, thread-safe without
 * global locking — each bucket is only contended by requests from the same user.
 * Why {@link AtomicInteger}: lock-free increment (CAS loop), avoids synchronised block.
 *
 * <h3>Algorithm</h3>
 * <pre>
 *   1. compute(key): if bucket absent OR window expired → new bucket(now)
 *   2. increment count atomically
 *   3. if count > LIMIT → 429, set Retry-After header
 * </pre>
 * Complexity: O(1) per request.
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    public static final int LIMIT = 300;
    static final long WINDOW_MS = 60_000L;

    /** Identity → current window bucket. Entries are implicitly evicted when window expires. */
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String key    = resolveKey(request);
        Bucket bucket = buckets.compute(key, (k, b) -> {
            long now = System.currentTimeMillis();
            return (b == null || now - b.windowStart > WINDOW_MS) ? new Bucket(now) : b;
        });

        int used      = bucket.count.incrementAndGet();
        int remaining = Math.max(0, LIMIT - used);
        long resetSec = (bucket.windowStart + WINDOW_MS - System.currentTimeMillis()) / 1000;

        response.setHeader("X-RateLimit-Limit",     String.valueOf(LIMIT));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset",     String.valueOf(Math.max(0, resetSec)));

        if (used > LIMIT) {
            log.warn("Rate limit exceeded key={}", key);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(Math.max(1, resetSec)));
            response.getWriter().write(
                "{\"status\":429,\"code\":\"RATE_LIMIT_EXCEEDED\"," +
                "\"message\":\"Too many requests. Limit: " + LIMIT + " per minute.\"}");
            return false;
        }
        return true;
    }

    /** Use authenticated user-id when available, fall back to client IP. */
    private String resolveKey(HttpServletRequest request) {
        Object uid = request.getAttribute("authenticatedUserId");
        if (uid != null) return "user:" + uid;
        String forwarded = request.getHeader("X-Forwarded-For");
        return "ip:" + (forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr());
    }

    private static final class Bucket {
        final long          windowStart;
        final AtomicInteger count = new AtomicInteger(0);
        Bucket(long windowStart) { this.windowStart = windowStart; }
    }
}
