package com.architect.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects outbound HTTP calls from backend services in multiple languages.
 *
 * <h2>Supported runtimes</h2>
 * <ul>
 *   <li><b>Java</b>   — WebClient {@code .uri()}, RestTemplate, {@code URI.create()}</li>
 *   <li><b>Node.js</b> — axios, got, needle, superagent, node-fetch / undici {@code fetch()}</li>
 *   <li><b>Python</b>  — requests, httpx, aiohttp</li>
 * </ul>
 *
 * <h2>Why this is separate from FrontendCallDetectorService</h2>
 * Frontend code makes relative calls ({@code /api/users}) because the browser resolves them
 * against the host. Backend services call each other with full URLs
 * ({@code http://catalog-service:3001/api/properties}) because there is no browser.
 * Separating detectors avoids false positives and keeps each detector focused.
 */
@Slf4j
@Service
public class BackendHttpCallDetectorService {

    @Data
    public static class DetectedBackendCall {
        private String urlPattern;
        private String httpMethod;
        private String callType;
        private String filePath;
        private int    lineNumber;
    }

    public List<DetectedBackendCall> detect(String content, String filePath) {
        List<DetectedBackendCall> out = new ArrayList<>();
        if (filePath == null || content == null) return out;

        if      (filePath.endsWith(".java"))     detectJava(content, filePath, out);
        else if (isNodeFile(filePath))           detectNode(content, filePath, out);
        else if (filePath.endsWith(".py"))       detectPython(content, filePath, out);

        return out;
    }

    // ─── Java: WebClient, RestTemplate, URI.create ───────────────────────────

    private static final Pattern JAVA_URI_STRING = Pattern.compile(
            "\\.uri\\s*\\(\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");

    private static final Pattern JAVA_RT_URL = Pattern.compile(
            "restTemplate\\s*\\.\\s*(get|post|put|delete|patch)(?:ForObject|ForEntity|ForLocation)?\\s*\\(\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern JAVA_URI_CREATE = Pattern.compile(
            "URI\\.create\\s*\\(\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private void detectJava(String content, String filePath, List<DetectedBackendCall> out) {
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int ln = i + 1;

            Matcher uriM = JAVA_URI_STRING.matcher(line);
            while (uriM.find()) {
                String path = uriM.group(1);
                if (looksLikeUrl(path))
                    out.add(call(path, guessMethodJava(line), "java-webclient-uri", filePath, ln));
            }

            Matcher rtM = JAVA_RT_URL.matcher(line);
            while (rtM.find()) {
                String path = rtM.group(2);
                String m    = rtM.group(1).toUpperCase();
                if (looksLikeUrl(path))
                    out.add(call(path, m, "java-resttemplate", filePath, ln));
            }

            Matcher createM = JAVA_URI_CREATE.matcher(line);
            while (createM.find()) {
                String path = createM.group(1);
                if (looksLikeUrl(path))
                    out.add(call(path, "GET", "java-uri-create", filePath, ln));
            }
        }
    }

    // ─── Node.js: axios, got, superagent, needle, node-fetch / undici ────────

    /** axios.get('url'), axios.post('url') */
    private static final Pattern NODE_AXIOS = Pattern.compile(
            "axios\\s*\\.\\s*(get|post|put|delete|patch|request)\\s*\\(\\s*([\"'`])(.+?)\\2",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** axios({ url: '...' }) — object form */
    private static final Pattern NODE_AXIOS_OBJ_URL = Pattern.compile(
            "\\burl\\s*:\\s*([\"'`])(.+?)\\1",
            Pattern.CASE_INSENSITIVE);

    /** got('url'), got.get('url'), got.post('url') */
    private static final Pattern NODE_GOT = Pattern.compile(
            "\\bgot\\s*(?:\\.\\s*(get|post|put|delete|patch))?\\s*\\(\\s*([\"'`])(.+?)\\2",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * fetch('http://svc/path') — node-fetch / undici.
     * Only matches absolute URLs; relative paths are handled by FrontendCallDetectorService.
     */
    private static final Pattern NODE_FETCH_ABS = Pattern.compile(
            "\\bfetch\\s*\\(\\s*([\"'`])(https?://.+?)\\1",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** superagent: request.get('url'), needle: needle('get', 'url') */
    private static final Pattern NODE_SUPERAGENT = Pattern.compile(
            "\\b(?:request|needle)\\s*(?:\\(\\s*[\"']?(get|post|put|delete|patch)[\"']?\\s*," +
            "\\s*([\"'`])(.+?)\\2|\\s*\\.\\s*(get|post|put|delete|patch)\\s*\\(\\s*([\"'`])(.+?)\\5)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** ky: ky.get('url'), ky('url'), ky.post('url') — popular fetch wrapper */
    private static final Pattern NODE_KY = Pattern.compile(
            "\\bky\\s*(?:\\.\\s*(get|post|put|delete|patch|head))?\\s*\\(\\s*([\"'`])(.+?)\\2",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** ofetch / $fetch (Nuxt): ofetch('/api/data'), $fetch('/api/data') */
    private static final Pattern NODE_OFETCH = Pattern.compile(
            "(?:\\$fetch|ofetch|\\$ofetch)\\s*\\(\\s*([\"'`])(.+?)\\1",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** undici: request('url'), fetch from undici */
    private static final Pattern NODE_UNDICI = Pattern.compile(
            "\\bundici\\.(?:request|fetch)\\s*\\(\\s*([\"'`])(.+?)\\1",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private void detectNode(String content, String filePath, List<DetectedBackendCall> out) {
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // Skip comment lines
            if (line.trim().startsWith("//") || line.trim().startsWith("*")) continue;
            int ln = i + 1;

            Matcher axM = NODE_AXIOS.matcher(line);
            while (axM.find()) {
                String method = axM.group(1).toUpperCase();
                if ("REQUEST".equals(method)) method = guessMethodNode(line);
                String url = collapse(axM.group(3));
                if (looksLikeUrl(url))
                    out.add(call(url, method, "node-axios", filePath, ln));
            }

            Matcher axObj = NODE_AXIOS_OBJ_URL.matcher(line);
            while (axObj.find()) {
                String url = collapse(axObj.group(2));
                if (looksLikeUrl(url))
                    out.add(call(url, guessMethodNode(line), "node-axios-obj", filePath, ln));
            }

            Matcher gotM = NODE_GOT.matcher(line);
            while (gotM.find()) {
                String method = gotM.group(1) != null ? gotM.group(1).toUpperCase() : "GET";
                String url    = collapse(gotM.group(3));
                if (looksLikeUrl(url))
                    out.add(call(url, method, "node-got", filePath, ln));
            }

            Matcher fetchM = NODE_FETCH_ABS.matcher(line);
            while (fetchM.find()) {
                String url = collapse(fetchM.group(2));
                if (looksLikeUrl(url))
                    out.add(call(url, guessMethodNode(line), "node-fetch", filePath, ln));
            }

            Matcher sgM = NODE_SUPERAGENT.matcher(line);
            while (sgM.find()) {
                if (sgM.group(3) != null) {
                    String url = collapse(sgM.group(3));
                    if (looksLikeUrl(url))
                        out.add(call(url, sgM.group(1).toUpperCase(), "node-superagent", filePath, ln));
                }
                if (sgM.group(6) != null) {
                    String url = collapse(sgM.group(6));
                    if (looksLikeUrl(url))
                        out.add(call(url, sgM.group(4).toUpperCase(), "node-superagent", filePath, ln));
                }
            }

            Matcher kyM = NODE_KY.matcher(line);
            while (kyM.find()) {
                String method = kyM.group(1) != null ? kyM.group(1).toUpperCase() : "GET";
                String url = collapse(kyM.group(3));
                if (looksLikeUrl(url))
                    out.add(call(url, method, "node-ky", filePath, ln));
            }

            Matcher ofM = NODE_OFETCH.matcher(line);
            while (ofM.find()) {
                String url = collapse(ofM.group(2));
                if (looksLikeUrl(url))
                    out.add(call(url, guessMethodNode(line), "node-ofetch", filePath, ln));
            }

            Matcher unM = NODE_UNDICI.matcher(line);
            while (unM.find()) {
                String url = collapse(unM.group(2));
                if (looksLikeUrl(url))
                    out.add(call(url, guessMethodNode(line), "node-undici", filePath, ln));
            }
        }
    }

    // ─── Python: requests, httpx, aiohttp ────────────────────────────────────

    /** requests.get('url'), httpx.post('url'), session.get('url'), client.get(f"url") */
    private static final Pattern PY_REQUESTS = Pattern.compile(
            "(?:requests|httpx|session|client)\\s*\\.\\s*(get|post|put|delete|patch)\\s*\\(\\s*f?([\"'])(.+?)\\2",
            Pattern.CASE_INSENSITIVE);

    /** aiohttp: session.request('GET', 'url') */
    private static final Pattern PY_AIOHTTP = Pattern.compile(
            "(?:session|client)\\s*\\.\\s*request\\s*\\(\\s*[\"'](GET|POST|PUT|DELETE|PATCH)[\"']\\s*,\\s*[\"'](.+?)[\"']",
            Pattern.CASE_INSENSITIVE);

    private void detectPython(String content, String filePath, List<DetectedBackendCall> out) {
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("#")) continue;
            int ln = i + 1;

            Matcher reqM = PY_REQUESTS.matcher(line);
            while (reqM.find()) {
                String url = reqM.group(3);
                if (looksLikeUrl(url))
                    out.add(call(url, reqM.group(1).toUpperCase(), "python-requests", filePath, ln));
            }

            Matcher aioM = PY_AIOHTTP.matcher(line);
            while (aioM.find()) {
                String url = aioM.group(2);
                if (looksLikeUrl(url))
                    out.add(call(url, aioM.group(1).toUpperCase(), "python-aiohttp", filePath, ln));
            }
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static boolean isNodeFile(String path) {
        return path.endsWith(".js") || path.endsWith(".ts")
            || path.endsWith(".mjs") || path.endsWith(".cjs");
    }

    private static boolean looksLikeUrl(String s) {
        if (s == null || s.isBlank()) return false;
        String t = s.trim();
        return t.startsWith("http://") || t.startsWith("https://") || t.startsWith("/")
            || (t.startsWith("${") && t.contains("/"))   // JS/Java env-var: ${SERVICE_URL}/api/path
            || (t.startsWith("{") && t.contains("/"));   // Python f-string: {REGISTRY}/register
    }

    private static String guessMethodJava(String line) {
        String l = line.toLowerCase();
        if (l.contains(".post(") || l.contains("postFor")) return "POST";
        if (l.contains(".put("))    return "PUT";
        if (l.contains(".delete(")) return "DELETE";
        if (l.contains(".patch("))  return "PATCH";
        return "GET";
    }

    private static String guessMethodNode(String line) {
        String l = line.toLowerCase();
        if (l.contains("method:'post'")  || l.contains("method: 'post'")  || l.contains("method:\"post\""))  return "POST";
        if (l.contains("method:'put'")   || l.contains("method: 'put'"))   return "PUT";
        if (l.contains("method:'delete'")|| l.contains("method: 'delete'")) return "DELETE";
        if (l.contains("method:'patch'") || l.contains("method: 'patch'"))  return "PATCH";
        return "GET";
    }

    /** Collapse template literals / multiline to a single-line pattern. */
    private static String collapse(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\s+", " ").trim();
    }

    private static DetectedBackendCall call(String url, String method, String type, String file, int line) {
        DetectedBackendCall c = new DetectedBackendCall();
        c.setUrlPattern(url.trim());
        c.setHttpMethod(method);
        c.setCallType(type);
        c.setFilePath(file);
        c.setLineNumber(line);
        return c;
    }
}
