package com.architect.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-JVM execution slots (max 2 per user, 10 global) while a scan body runs.
 * Authoritative cross-instance limits are enforced in {@link ScanQueueService#claimNext}.
 * If {@link #tryAcquire} fails after a row was claimed, the worker requeues the task.
 */
@Service
public class ScanConcurrencyService {

    private static final int MAX_PER_USER = 2;
    private static final int MAX_GLOBAL = 10;

    private final ConcurrentHashMap<Long, AtomicInteger> userActiveScans = new ConcurrentHashMap<>();
    private final AtomicInteger globalActiveScans = new AtomicInteger();

    public boolean tryAcquire(Long userId) {
        for (;;) {
            int g = globalActiveScans.get();
            if (g >= MAX_GLOBAL) {
                return false;
            }
            AtomicInteger u = userActiveScans.computeIfAbsent(userId, k -> new AtomicInteger(0));
            int uc = u.get();
            if (uc >= MAX_PER_USER) {
                return false;
            }
            if (!u.compareAndSet(uc, uc + 1)) {
                continue;
            }
            if (!globalActiveScans.compareAndSet(g, g + 1)) {
                u.decrementAndGet();
                continue;
            }
            return true;
        }
    }

    public void release(Long userId) {
        globalActiveScans.decrementAndGet();
        userActiveScans.computeIfPresent(userId, (k, v) -> {
            int n = v.decrementAndGet();
            return n <= 0 ? null : v;
        });
    }

    public int globalActive() {
        return globalActiveScans.get();
    }

    public int userActive(long userId) {
        AtomicInteger u = userActiveScans.get(userId);
        return u == null ? 0 : u.get();
    }
}
