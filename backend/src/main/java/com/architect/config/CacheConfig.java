package com.architect.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * In-process cache configuration using Caffeine.
 *
 * <h3>Why Caffeine?</h3>
 * Caffeine is a high-performance, near-optimal caching library (W-TinyLFU eviction
 * policy, O(1) amortised get/put).  It is the default recommendation for Spring Boot
 * when Redis is not needed — zero infrastructure, no serialisation overhead.
 *
 * <h3>Caches defined</h3>
 * <table>
 *   <tr><th>Cache name</th><th>TTL</th><th>Max size</th><th>What</th></tr>
 *   <tr><td>{@code graph}</td>    <td>5 min</td><td>50 entries</td><td>Full dependency graph per user</td></tr>
 *   <tr><td>{@code riskOverview}</td><td>2 min</td><td>50 entries</td><td>Risk cards for dashboard</td></tr>
 *   <tr><td>{@code insights}</td><td>10 min</td><td>50 entries</td><td>Architecture metrics / stats</td></tr>
 * </table>
 *
 * <h3>Eviction</h3>
 * Time-based TTL is a safety net.  The primary invalidation trigger is
 * {@code @CacheEvict(value = "graph", key = "#user.id")} called at the end of
 * every scan in {@link com.architect.service.RepoScannerService}.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // graph — most expensive to build; evicted after each scan
        manager.registerCustomCache("graph",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(50)
                        .recordStats()
                        .build());

        // riskOverview — lightweight dashboard cards
        manager.registerCustomCache("riskOverview",
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumSize(50)
                        .build());

        // insights — architecture metrics; computed from DB aggregates
        manager.registerCustomCache("insights",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(50)
                        .build());

        return manager;
    }
}
