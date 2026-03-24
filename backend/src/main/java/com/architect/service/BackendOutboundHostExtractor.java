package com.architect.service;

import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds backend-to-backend URL targets: {@code http://user-service:8080}, Feign clients, service URLs in yml.
 */
@Service
public class BackendOutboundHostExtractor {

    private static final Pattern HTTP_HOST = Pattern.compile(
            "https?://([a-zA-Z][a-zA-Z0-9.-]{1,120})(?::(\\d{1,5}))?(?=[/\"'\\s]|$)");

    private static final Pattern FEIGN_NAME_VALUE = Pattern.compile(
            "@FeignClient\\s*\\([^)]*?\\b(?:name|value)\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** @FeignClient("user-service") */
    private static final Pattern FEIGN_STRING = Pattern.compile(
            "@FeignClient\\s*\\(\\s*[\"']([a-zA-Z][a-zA-Z0-9_-]{1,62})[\"']\\s*\\)");

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "example.com", "example.org",
            "www.w3.org", "github.com", "api.github.com", "raw.githubusercontent.com",
            "google.com", "www.google.com", "googleapis.com", "gstatic.com",
            "amazonaws.com", "cloudfront.net", "stripe.com", "api.stripe.com",
            "anthropic.com", "openai.com", "npmjs.org", "unpkg.com", "jsdelivr.net",
            "fonts.googleapis.com", "fonts.gstatic.com");

    @Value
    @Builder
    public static class OutboundHost {
        String host;           // user-service or user-service:8080
        String sourceKind;    // java-http | java-feign | config-yml
        int lineNumber;
    }

    public List<OutboundHost> extract(String content, String filePath) {
        if (content == null || filePath == null) return List.of();
        Set<String> seen = new HashSet<>();
        List<OutboundHost> out = new ArrayList<>();

        if (filePath.endsWith(".java")) {
            extractHttpHostsFromJava(content, seen, out);
            extractFeign(content, seen, out);
        }
        if (filePath.endsWith(".yml") || filePath.endsWith(".yaml")
                || filePath.endsWith(".properties") || filePath.endsWith(".env")
                || filePath.endsWith(".env.local")) {
            extractFromConfigLines(content, filePath, seen, out);
        }
        return out;
    }

    private void extractHttpHostsFromJava(String content, Set<String> seen, List<OutboundHost> out) {
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("jdbc:") || line.contains("// Log") || line.trim().startsWith("//")) {
                continue;
            }
            Matcher m = HTTP_HOST.matcher(line);
            while (m.find()) {
                String host = m.group(1);
                String port = m.group(2);
                if (isBlocked(host)) continue;
                if (!looksLikeServiceHost(host)) continue;
                String key = host + ":" + (port != null ? port : "");
                if (!seen.add("http:" + key + ":" + (i + 1))) continue;
                String value = port != null ? host + ":" + port : host;
                out.add(OutboundHost.builder().host(value).sourceKind("java-http").lineNumber(i + 1).build());
            }
        }
    }

    private void extractFeign(String content, Set<String> seen, List<OutboundHost> out) {
        Matcher m = FEIGN_STRING.matcher(content);
        while (m.find()) {
            String name = m.group(1).trim();
            if (name.contains(".")) continue;
            if (!seen.add("feign:" + name)) continue;
            out.add(OutboundHost.builder().host(name).sourceKind("java-feign").lineNumber(lineOf(content, m.start())).build());
        }
        m = FEIGN_NAME_VALUE.matcher(content);
        while (m.find()) {
            String name = m.group(1).trim();
            if (name.contains(".") || name.contains("/") || name.length() > 64) continue;
            if (!seen.add("feign:" + name)) continue;
            out.add(OutboundHost.builder().host(name).sourceKind("java-feign").lineNumber(lineOf(content, m.start())).build());
        }
    }

    private void extractFromConfigLines(String content, String path, Set<String> seen, List<OutboundHost> out) {
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("jdbc:") || lower.contains("mongodb://") || lower.contains("redis://")
                    || lower.contains("kafka://") || lower.contains("rabbitmq://")) {
                continue;
            }
            if (lower.contains("datasource") && lower.contains("url")) continue;
            Matcher m = HTTP_HOST.matcher(line);
            while (m.find()) {
                String host = m.group(1);
                String port = m.group(2);
                if (isBlocked(host)) continue;
                if (!looksLikeServiceHost(host)) continue;
                String value = port != null ? host + ":" + port : host;
                String sig = "cfg:" + value + ":" + (i + 1);
                if (!seen.add(sig)) continue;
                out.add(OutboundHost.builder().host(value).sourceKind("config-yml").lineNumber(i + 1).build());
            }
        }
    }

    /**
     * Docker/K8s style names (hyphenated service ids) or short alnum; exclude obvious public domains.
     */
    private static boolean looksLikeServiceHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.contains("www.") || h.endsWith(".com") || h.endsWith(".io") || h.endsWith(".net")) {
            return h.contains("-service") || h.matches("[a-z]+-[a-z]+-[a-z]+.*");
        }
        return h.contains("-") || h.length() <= 40;
    }

    private static boolean isBlocked(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(h)) return true;
        if (h.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) return true;
        for (String b : BLOCKED_HOSTS) {
            if (h.endsWith("." + b) || h.equals(b)) return true;
        }
        if (h.endsWith(".amazonaws.com") || h.endsWith(".googleapis.com") || h.endsWith(".githubusercontent.com")) {
            return true;
        }
        return false;
    }

    private static int lineOf(String content, int index) {
        int n = 1;
        for (int i = 0; i < index && i < content.length(); i++) {
            if (content.charAt(i) == '\n') n++;
        }
        return n;
    }
}
