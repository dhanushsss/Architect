package com.architect.service;

import java.net.URI;
import java.util.Set;

/** Normalizes URL patterns and classifies outbound calls. */
public final class ApiCallUrlNormalizer {

    public static final String KIND_EXTERNAL   = "EXTERNAL";
    public static final String KIND_UNRESOLVED = "UNRESOLVED";
    public static final String KIND_INTERNAL   = "INTERNAL_ENDPOINT";

    /** Public TLDs that definitively indicate an external host. */
    private static final Set<String> PUBLIC_TLDS = Set.of(
            "com", "net", "org", "io", "dev", "app", "co", "ai",
            "cloud", "edu", "gov", "mil", "int", "info", "biz",
            "tech", "online", "store", "site", "me"
    );

    private ApiCallUrlNormalizer() {}

    /** True only for public absolute hosts (not localhost/internal service names). */
    public static boolean isExternalAbsoluteUrl(String raw) {
        if (raw == null) return false;
        String t = raw.trim().toLowerCase();
        if (!t.startsWith("http://") && !t.startsWith("https://")) return false;
        String host = extractHostOnly(t);
        if (host == null || host.isBlank()) return false;

        // localhost / loopback → never external
        if (host.equals("localhost") || host.startsWith("127.") || host.equals("0.0.0.0")) return false;

        // Raw IPv4 (e.g. 10.0.0.5) → private network, not external
        if (host.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) return false;

        // No dots → Docker/K8s service name (catalog-service, user-api, booking-registry)
        // These have NO public TLD and are resolved inside the cluster
        if (!host.contains(".")) return false;

        // Has dots → check for a known public TLD
        String tld = host.substring(host.lastIndexOf('.') + 1);
        return PUBLIC_TLDS.contains(tld);
    }

    /**
     * Extract the host portion from an absolute URL for display / logging.
     * Returns {@code null} if the URL is not absolute or cannot be parsed.
     */
    public static String extractExternalHost(String absoluteUrl) {
        if (absoluteUrl == null || absoluteUrl.isBlank()) return null;
        return extractHostOnly(absoluteUrl.trim().toLowerCase());
    }

    /** Converts URL/path values into a normalized path used for endpoint matching. */
    public static String normalizeForMatching(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String s = raw.trim();

        // Step 1 — strip host from absolute URLs so /api/users matches http://svc:8080/api/users
        if (s.startsWith("http://") || s.startsWith("https://")) {
            try {
                URI u = URI.create(s);
                String path = u.getPath();
                s = (path == null || path.isBlank()) ? "/" : path;
            } catch (Exception ignored) {
                // Fallback: manually find the first slash after "scheme://"
                int afterScheme = s.indexOf("//") + 2;
                int firstSlash  = s.indexOf('/', afterScheme);
                s = firstSlash >= 0 ? s.substring(firstSlash) : "/";
            }
        }

        // Step 2 — strip query string
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);

        // Step 3 — template / path variables → wildcard
        s = s.replaceAll("\\$\\{[^}]+}", "*");
        s = s.replaceAll("\\$[a-zA-Z_][a-zA-Z0-9_]*", "*");
        s = s.replaceAll("\\{[^/]+}", "*");   // {id}, {userId}, etc.

        // Collapse consecutive wildcards
        while (s.contains("**")) s = s.replace("**", "*");

        // Step 4 — clean up slashes
        s = s.replaceAll("/+", "/");
        if (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (!s.startsWith("/")) s = "/" + s;

        return s;
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    private static String extractHostOnly(String lowerUrl) {
        try {
            URI u = URI.create(lowerUrl);
            return u.getHost();
        } catch (Exception e) {
            // Manual extraction: between "//" and the next "/" or ":"
            int start = lowerUrl.indexOf("//");
            if (start < 0) return null;
            start += 2;
            int end   = lowerUrl.length();
            int slash = lowerUrl.indexOf('/', start);
            int colon = lowerUrl.indexOf(':', start);
            if (slash > 0 && slash < end) end = slash;
            if (colon > 0 && colon < end) end = colon;
            return (start < end) ? lowerUrl.substring(start, end) : null;
        }
    }
}
