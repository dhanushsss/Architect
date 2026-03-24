package com.architect.config;

import com.architect.interceptor.RateLimitInterceptor;
import com.architect.interceptor.RequestTrackingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC interceptor chain registration.
 *
 * <pre>
 * Request                      Response
 *   │                              ▲
 *   ▼                              │
 * RequestTrackingInterceptor ──────┘  (all /api/**)
 *   │ records startMs; logs on completion; sets X-Response-Time-Ms
 *   ▼
 * RateLimitInterceptor         (versioned /api/v1/** only)
 *   │ token-bucket per user; short-circuits at 429
 *   ▼
 * Controller
 * </pre>
 *
 * Auth, webhook, and public endpoints are excluded from rate limiting because:
 * - {@code /api/auth/**}     — GitHub OAuth redirect, must never be throttled
 * - {@code /api/webhooks/**} — GitHub sends webhooks; our IP is in their allowlist
 * - {@code /api/public/**}   — public read-only, separate rate-limit concern
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequestTrackingInterceptor requestTrackingInterceptor;
    private final RateLimitInterceptor       rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestTrackingInterceptor)
                .addPathPatterns("/api/**");

        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/v1/**");
    }
}
