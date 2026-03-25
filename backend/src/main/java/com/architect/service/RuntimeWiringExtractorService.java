package com.architect.service;

import com.architect.model.RuntimeWiringFact;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts how services are wired at runtime via URLs/config (registry, gateway, UI proxy) — not Git.
 */
@Slf4j
@Service
public class RuntimeWiringExtractorService {

    @Value
    @Builder
    public static class ExtractedFact {
        String factType;
        String factKey;
        String factValue;
        int lineNumber;
    }

    public List<ExtractedFact> extract(String content, String filePath) {
        List<ExtractedFact> out = new ArrayList<>();
        if (content == null || content.isBlank() || filePath == null) return out;

        String lower = filePath.toLowerCase();
        if (lower.endsWith(".properties") || lower.contains("application") && lower.endsWith(".properties")) {
            out.addAll(fromProperties(content, filePath));
        }
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            out.addAll(fromYaml(content, filePath));
        }
        if (lower.contains("vite.config") && (lower.endsWith(".ts") || lower.endsWith(".js") || lower.endsWith(".mts"))) {
            out.addAll(fromViteConfig(content, filePath));
        }
        if (lower.endsWith(".env") || lower.endsWith(".env.local") || lower.endsWith(".env.development")) {
            out.addAll(fromEnv(content, filePath));
        }
        if (lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".mjs")) {
            out.addAll(fromJsGatewayFile(content, filePath));
        }
        return out;
    }

    private List<ExtractedFact> fromProperties(String content, String path) {
        List<ExtractedFact> out = new ArrayList<>();
        String[] lines = content.split("\n");
        Pattern appName = Pattern.compile("(?i)^\\s*spring\\.application\\.name\\s*=\\s*(\\S+)\\s*$");
        Pattern port = Pattern.compile("(?i)^\\s*server\\.port\\s*=\\s*(\\d+)\\s*$");
        Pattern eureka = Pattern.compile("(?i)^\\s*eureka\\.client\\.serviceUrl\\.defaultZone\\s*=\\s*(\\S+)\\s*$");
        Pattern regUrl = Pattern.compile("(?i)^\\s*REGISTRY_URL\\s*=\\s*(\\S+)\\s*$");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher m;
            if ((m = appName.matcher(line)).find()) {
                out.add(f(RuntimeWiringFact.APP_NAME, null, m.group(1), i + 1));
            }
            if ((m = port.matcher(line)).find()) {
                out.add(f(RuntimeWiringFact.SERVER_PORT, null, m.group(1), i + 1));
            }
            if ((m = eureka.matcher(line)).find()) {
                out.add(f(RuntimeWiringFact.EUREKA_REGISTRY, null, cleanUrl(m.group(1)), i + 1));
            }
            if ((m = regUrl.matcher(line)).find()) {
                out.add(f(RuntimeWiringFact.EUREKA_REGISTRY, "REGISTRY_URL", cleanUrl(m.group(1)), i + 1));
            }
        }
        return out;
    }

    private List<ExtractedFact> fromYaml(String content, String path) {
        List<ExtractedFact> out = new ArrayList<>();
        // spring.application.name (multiline block)
        Matcher appBlock = Pattern.compile(
                "(?is)spring:\\s*\\n\\s*application:\\s*\\n\\s*name:\\s*(\\S+)").matcher(content);
        while (appBlock.find()) {
            out.add(f(RuntimeWiringFact.APP_NAME, null, appBlock.group(1).trim(), lineOf(content, appBlock.start())));
        }
        // single-line style in yaml: "spring.application.name: foo" (unusual)
        Matcher appInline = Pattern.compile("(?m)^\\s*spring\\.application\\.name:\\s*(\\S+)").matcher(content);
        while (appInline.find()) {
            out.add(f(RuntimeWiringFact.APP_NAME, null, appInline.group(1).trim(), lineOf(content, appInline.start())));
        }

        Matcher portM = Pattern.compile("(?is)server:\\s*\\n\\s*port:\\s*(\\d+)").matcher(content);
        while (portM.find()) {
            out.add(f(RuntimeWiringFact.SERVER_PORT, null, portM.group(1), lineOf(content, portM.start())));
        }
        Matcher portInline = Pattern.compile("(?m)^\\s*server\\.port:\\s*(\\d+)").matcher(content);
        while (portInline.find()) {
            out.add(f(RuntimeWiringFact.SERVER_PORT, null, portInline.group(1), lineOf(content, portInline.start())));
        }

        Matcher eurekaM = Pattern.compile(
                "(?is)defaultZone:\\s*(\\S+)").matcher(content);
        while (eurekaM.find()) {
            String z = cleanUrl(eurekaM.group(1));
            if (z.contains("8761") || z.contains("eureka") || z.contains("registry")) {
                out.add(f(RuntimeWiringFact.EUREKA_REGISTRY, null, z, lineOf(content, eurekaM.start())));
            }
        }

        // Spring Cloud Gateway: lb://service-id with nearby Path=
        if (content.contains("gateway") || content.contains("Gateway")) {
            Pattern lb = Pattern.compile("lb://([a-zA-Z0-9_.-]+)");
            Matcher lm = lb.matcher(content);
            while (lm.find()) {
                String svc = lm.group(1);
                int pos = lm.start();
                String window = content.substring(Math.max(0, pos - 1200), pos);
                String pathPat = lastMatch(window, Pattern.compile("Path[=:]\\s*([^\\n#]+)"));
                if (pathPat == null) {
                    pathPat = lastMatch(window, Pattern.compile("(?i)path:\\s*([^\n#]+)"));
                }
                String p = pathPat != null ? pathPat.trim().replace("\"", "").replace("'", "") : "*";
                out.add(f(RuntimeWiringFact.GATEWAY_ROUTE, p, svc, lineOf(content, pos)));
            }
            // http://hostname:port style routes
            Pattern httpUri = Pattern.compile("uri:\\s*https?://([a-zA-Z0-9_.-]+):(\\d+)");
            Matcher hm = httpUri.matcher(content);
            while (hm.find()) {
                String host = hm.group(1);
                if (!host.equals("localhost") && !host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                    out.add(f(RuntimeWiringFact.GATEWAY_ROUTE, "*", host, lineOf(content, hm.start())));
                }
            }
        }
        return out;
    }

    private static String lastMatch(String s, Pattern p) {
        String last = null;
        Matcher m = p.matcher(s);
        while (m.find()) {
            last = m.group(1);
        }
        return last;
    }

    private List<ExtractedFact> fromViteConfig(String content, String path) {
        List<ExtractedFact> out = new ArrayList<>();
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            Matcher tm = Pattern.compile("target:\\s*['\"]([^'\"]+)['\"]").matcher(lines[i]);
            if (!tm.find()) continue;
            String target = tm.group(1).trim();
            String proxyPath = null;
            for (int j = Math.max(0, i - 20); j < i; j++) {
                Matcher pm = Pattern.compile("['\"]([^'\"]+)['\"]\\s*:\\s*\\{").matcher(lines[j]);
                if (pm.find()) {
                    String key = pm.group(1);
                    if (key.startsWith("/")) {
                        proxyPath = key;
                    }
                }
            }
            out.add(f(RuntimeWiringFact.VITE_PROXY, proxyPath != null ? proxyPath : "/api", target, i + 1));
        }
        return out;
    }

    /**
     * Detects Express/Node.js proxy routing maps like:
     * <pre>
     * const ROUTE_TO_SERVICE = {
     *   bookings: "booking-service",
     *   users:    "user-service",
     * };
     * </pre>
     * Creates GATEWAY_ROUTE facts so the graph shows api-gateway → downstream service edges.
     */
    private static final Pattern JS_ROUTE_MAP_ENTRY = Pattern.compile(
            "['\"]?([a-zA-Z][a-zA-Z0-9_-]+)['\"]?\\s*:\\s*['\"]([a-zA-Z][a-zA-Z0-9_-]*(?:-service|-gateway|-registry|-api|-server|-client))['\"]",
            Pattern.CASE_INSENSITIVE);

    private List<ExtractedFact> fromJsGatewayFile(String content, String path) {
        List<ExtractedFact> out = new ArrayList<>();
        // Only process files that look like server/gateway entry points
        String l = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        String filename = l.contains("/") ? l.substring(l.lastIndexOf('/') + 1) : l;
        boolean isServerFile = filename.equals("server.js") || filename.equals("server.ts")
                || filename.equals("server.mjs") || filename.equals("gateway.js")
                || filename.equals("gateway.ts") || filename.equals("index.js")
                || filename.equals("index.ts") || filename.equals("app.js")
                || filename.equals("app.ts");
        if (!isServerFile) return out;

        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("//") || line.trim().startsWith("*")) continue;
            Matcher m = JS_ROUTE_MAP_ENTRY.matcher(line);
            while (m.find()) {
                String segment = m.group(1).trim();
                String serviceName = m.group(2).trim();
                // Skip if segment looks like a JS property name that's not a path segment
                if (segment.length() > 32 || segment.contains(".")) continue;
                String pathPattern = "/api/" + segment + "/**";
                out.add(f(RuntimeWiringFact.GATEWAY_ROUTE, pathPattern, serviceName, i + 1));
            }
        }
        return out;
    }

    private List<ExtractedFact> fromEnv(String content, String path) {
        List<ExtractedFact> out = new ArrayList<>();
        Pattern reg = Pattern.compile("(?i)^\\s*REGISTRY_URL\\s*=\\s*(\\S+)\\s*$");
        Pattern pub = Pattern.compile("(?i)^\\s*PUBLIC_URL\\s*=\\s*(\\S+)\\s*$");
        Pattern gw = Pattern.compile("(?i)^\\s*(GATEWAY_URL|VITE_API|API_GATEWAY)\\s*=\\s*(\\S+)\\s*$");
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            Matcher m;
            if ((m = reg.matcher(lines[i])).find()) {
                out.add(f(RuntimeWiringFact.EUREKA_REGISTRY, "REGISTRY_URL", cleanUrl(m.group(1)), i + 1));
            }
            if ((m = pub.matcher(lines[i])).find()) {
                out.add(f(RuntimeWiringFact.ENV_SERVICE_URL, "PUBLIC_URL", cleanUrl(m.group(1)), i + 1));
            }
            if ((m = gw.matcher(lines[i])).find()) {
                out.add(f(RuntimeWiringFact.ENV_SERVICE_URL, m.group(1), cleanUrl(m.group(2)), i + 1));
            }
        }
        return out;
    }

    private static ExtractedFact f(String type, String key, String value, int line) {
        return ExtractedFact.builder().factType(type).factKey(key).factValue(value).lineNumber(line).build();
    }

    private static String cleanUrl(String u) {
        if (u == null) return "";
        return u.replaceAll("^[\"']|[\"']$", "").trim();
    }

    private static int lineOf(String content, int index) {
        int n = 1;
        for (int i = 0; i < index && i < content.length(); i++) {
            if (content.charAt(i) == '\n') n++;
        }
        return n;
    }
}
