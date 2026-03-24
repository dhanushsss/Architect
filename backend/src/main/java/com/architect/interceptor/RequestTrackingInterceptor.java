package com.architect.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Logs every API request with method, URI, status, and elapsed time.
 * Also injects {@code X-Response-Time-Ms} and {@code X-Api-Version} headers
 * so frontend / monitoring tools can observe latency without a separate APM agent.
 *
 * <p>Data structure: per-request attribute stored in {@link HttpServletRequest}
 * (a single {@code long} timestamp). Zero heap allocation per request beyond
 * the attribute map Spring already maintains.
 */
@Slf4j
@Component
public class RequestTrackingInterceptor implements HandlerInterceptor {

    private static final String ATTR_START = "req.startMs";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        request.setAttribute(ATTR_START, System.currentTimeMillis());
        log.debug("→ {} {}", request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        Long start = (Long) request.getAttribute(ATTR_START);
        long elapsed = start != null ? System.currentTimeMillis() - start : -1;
        int status = response.getStatus();

        if (ex != null || status >= 500) {
            log.error("← {} {} {} {}ms", request.getMethod(), request.getRequestURI(), status, elapsed, ex);
        } else if (status >= 400) {
            log.warn("← {} {} {} {}ms", request.getMethod(), request.getRequestURI(), status, elapsed);
        } else {
            log.info("← {} {} {} {}ms", request.getMethod(), request.getRequestURI(), status, elapsed);
        }

        response.setHeader("X-Response-Time-Ms", String.valueOf(elapsed));
        response.setHeader("X-Api-Version", "1");
    }
}
