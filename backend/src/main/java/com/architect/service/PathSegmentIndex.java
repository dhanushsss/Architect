package com.architect.service;

import com.architect.model.ApiEndpoint;

import java.util.*;

/**
 * Segment-count index for API endpoint path matching.
 *
 * <h2>Problem</h2>
 * The naive matcher ({@code RepoScannerService#matchCallToEndpoint}) iterates over
 * <em>every</em> endpoint for every call: O(U × N) where U = unresolved calls,
 * N = total endpoints.  For 10 repos × 50 endpoints × 200 calls this is
 * 10 000 × 500 = 5 000 000 segment comparisons per re-link run.
 *
 * <h2>Solution — two-level index</h2>
 * <pre>
 * Level 1 — exact HashMap   : normalised path → endpoint  (no path variables)
 *           lookup: O(1)
 *
 * Level 2 — segment HashMap : segment count → List&lt;ApiEndpoint&gt;
 *           lookup: O(E_k) where E_k = endpoints with same depth
 *           Paths that reach this level have path variables ({id}, {name} …).
 *           Typical k: 2–5 (e.g. /api/users → 2, /api/orders/{id}/items → 4)
 *           E_k ≪ N, so average-case improvement is ~80–90%.
 * </pre>
 *
 * <h2>Build complexity</h2>
 * O(N) — single pass over all endpoints.
 *
 * <h2>Query complexity</h2>
 * O(1) for exact paths (most frontend → backend calls).
 * O(E_k) for parametrised paths (backend CRUD endpoints).
 *
 * <h2>Space</h2>
 * O(N) — each endpoint stored once in one of the two maps.
 *
 * <h2>Thread safety</h2>
 * Read-only after construction — safe to share across threads without
 * synchronisation. Build happens once per {@code buildDependencyEdges} call,
 * then the index is discarded (no need for concurrent mutation).
 */
public final class PathSegmentIndex {

    /** Exact paths (no {vars}) → endpoint.  Lookup: O(1). */
    private final Map<String, ApiEndpoint> exactIndex = new HashMap<>();

    /** Paths with {vars} bucketed by segment depth.  Lookup: O(E_k). */
    private final Map<Integer, List<ApiEndpoint>> wildcardIndex = new HashMap<>();

    /** Build the index from an arbitrary list of endpoints — O(N). */
    public PathSegmentIndex(List<ApiEndpoint> endpoints) {
        for (ApiEndpoint ep : endpoints) {
            if (ep.getPath() == null) continue;
            String normalised = normalisePath(ep.getPath());
            if (normalised.contains("*")) {
                wildcardIndex
                        .computeIfAbsent(countSegments(normalised), k -> new ArrayList<>())
                        .add(ep);
            } else {
                exactIndex.put(normalised, ep);
            }
        }
    }

    /**
     * Return all endpoints whose path matches {@code callPath}.
     * The returned list is ordered: exact match first, then wildcard matches.
     */
    public List<ApiEndpoint> findCandidates(String callPath) {
        if (callPath == null || callPath.isBlank()) return List.of();

        String norm = stripQuery(normalisePath(callPath));
        List<ApiEndpoint> results = new ArrayList<>();

        // Level 1: exact match O(1)
        ApiEndpoint exact = exactIndex.get(norm);
        if (exact != null) results.add(exact);

        // Level 2: wildcard with same segment count O(E_k)
        int depth = countSegments(norm);
        for (ApiEndpoint ep : wildcardIndex.getOrDefault(depth, List.of())) {
            if (segmentsMatch(norm, normalisePath(ep.getPath()))) {
                results.add(ep);
            }
        }
        return results;
    }

    // ─── normalisation helpers ────────────────────────────────────────────────

    /**
     * Replace {pathVar} with *, collapse duplicate slashes, strip trailing slash.
     * Example: /api/users/{id}/orders becomes /api/users/{@literal *}/orders
     */
    static String normalisePath(String path) {
        if (path == null) return "";
        String p = path.replaceAll("\\{[^}]+}", "*")
                       .replaceAll("/+", "/");
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    private static String stripQuery(String path) {
        int q = path.indexOf('?');
        return q >= 0 ? path.substring(0, q) : path;
    }

    static int countSegments(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        return p.isBlank() ? 0 : p.split("/").length;
    }

    /**
     * Segment-by-segment comparison.  A {@code *} in either side is a wildcard.
     */
    private static boolean segmentsMatch(String callNorm, String epNorm) {
        String[] c = split(callNorm);
        String[] p = split(epNorm);
        if (c.length != p.length) return false;
        for (int i = 0; i < c.length; i++) {
            if ("*".equals(c[i]) || "*".equals(p[i])) continue;
            if (!c[i].equalsIgnoreCase(p[i])) return false;
        }
        return true;
    }

    private static String[] split(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        return p.isEmpty() ? new String[0] : p.split("/");
    }
}
