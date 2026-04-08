package com.architect.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class FrontendCallDetectorService {

    @Data
    public static class DetectedCall {
        private String urlPattern;
        private String httpMethod;
        private String callType;
        private String filePath;
        private int lineNumber;
    }

    public List<DetectedCall> detect(String content, String filePath) {
        List<DetectedCall> results = new ArrayList<>();
        if (!isRelevantFile(filePath)) return results;

        results.addAll(detectFetch(content, filePath));
        results.addAll(detectAxios(content, filePath));
        results.addAll(detectChainedHttp(content, filePath));
        results.addAll(detectAngularHttp(content, filePath));
        results.addAll(detectVueHttp(content, filePath));
        results.addAll(detectTanstackQuery(content, filePath));
        results.addAll(detectSwr(content, filePath));
        return results;
    }

    private boolean isRelevantFile(String path) {
        return path.endsWith(".js") || path.endsWith(".ts") || path.endsWith(".jsx")
                || path.endsWith(".tsx") || path.endsWith(".vue") || path.endsWith(".svelte");
    }

    private List<DetectedCall> detectFetch(String content, String filePath) {
        List<DetectedCall> results = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        // fetch("...") fetch('...') fetch(`...`)
        Pattern p = Pattern.compile("fetch\\s*\\(\\s*([\"'`])(.+?)\\1", Pattern.DOTALL);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = p.matcher(lines[i]);
            while (m.find()) {
                String url = collapseTemplate(m.group(2));
                if (!isSkippableUrl(url)) {
                    DetectedCall call = new DetectedCall();
                    call.setUrlPattern(url);
                    call.setHttpMethod(detectMethod(lines[i]));
                    call.setCallType("fetch");
                    call.setFilePath(filePath);
                    call.setLineNumber(i + 1);
                    results.add(call);
                }
            }
        }
        return results;
    }

    private List<DetectedCall> detectAxios(String content, String filePath) {
        List<DetectedCall> results = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        Pattern p = Pattern.compile(
                "axios\\s*\\.\\s*(get|post|put|delete|patch|request)\\s*\\(\\s*([\"'`])(.+?)\\2",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = p.matcher(lines[i]);
            while (m.find()) {
                String method = m.group(1).toUpperCase();
                if ("REQUEST".equals(method)) method = "GET";
                String url = collapseTemplate(m.group(3));
                if (!isSkippableUrl(url)) {
                    DetectedCall call = new DetectedCall();
                    call.setUrlPattern(url);
                    call.setHttpMethod(method);
                    call.setCallType("axios");
                    call.setFilePath(filePath);
                    call.setLineNumber(i + 1);
                    results.add(call);
                }
            }
        }
        return results;
    }

    /** api.get('/path'), httpClient.post(`...`) — not generic .get( */
    private List<DetectedCall> detectChainedHttp(String content, String filePath) {
        List<DetectedCall> results = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        Pattern p = Pattern.compile(
                "(?:api|client|httpClient|http)\\s*\\.\\s*(get|post|put|delete|patch)\\s*\\(\\s*([\"'`])(.+?)\\2",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("router.") || line.contains("path:") && line.contains("element:")) continue;
            Matcher m = p.matcher(line);
            while (m.find()) {
                String url = collapseTemplate(m.group(3));
                if (!url.startsWith("/") && !url.startsWith("http")) continue;
                if (isSkippableUrl(url)) continue;
                DetectedCall call = new DetectedCall();
                call.setUrlPattern(url);
                call.setHttpMethod(m.group(1).toUpperCase());
                call.setCallType("http-client");
                call.setFilePath(filePath);
                call.setLineNumber(i + 1);
                results.add(call);
            }
        }
        return results;
    }

    private List<DetectedCall> detectAngularHttp(String content, String filePath) {
        List<DetectedCall> results = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        Pattern p = Pattern.compile(
                "this\\.http\\s*\\.\\s*(get|post|put|delete|patch)\\s*\\(\\s*([\"'`])(.+?)\\2",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = p.matcher(lines[i]);
            while (m.find()) {
                String url = collapseTemplate(m.group(3));
                if (!isSkippableUrl(url)) {
                    DetectedCall call = new DetectedCall();
                    call.setUrlPattern(url);
                    call.setHttpMethod(m.group(1).toUpperCase());
                    call.setCallType("angular-http");
                    call.setFilePath(filePath);
                    call.setLineNumber(i + 1);
                    results.add(call);
                }
            }
        }
        return results;
    }

    private List<DetectedCall> detectVueHttp(String content, String filePath) {
        List<DetectedCall> results = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        Pattern p = Pattern.compile(
                "(?:\\$http|useFetch|useAxios)\\s*(?:\\.|\\()\\s*(?:get|post|put|delete|patch)?\\s*([\"'`])(.+?)\\1",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = p.matcher(lines[i]);
            while (m.find()) {
                String url = collapseTemplate(m.group(2));
                if (!isSkippableUrl(url)) {
                    DetectedCall call = new DetectedCall();
                    call.setUrlPattern(url);
                    call.setHttpMethod("GET");
                    call.setCallType("vue-http");
                    call.setFilePath(filePath);
                    call.setLineNumber(i + 1);
                    results.add(call);
                }
            }
        }
        return results;
    }

    /** TanStack Query / React Query: useQuery({ queryFn: () => fetch('/api/...') }) */
    private List<DetectedCall> detectTanstackQuery(String content, String filePath) {
        List<DetectedCall> results = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        // queryKey/queryFn patterns often have fetch/axios inside arrow functions
        Pattern p = Pattern.compile(
                "(?:useQuery|useMutation|useInfiniteQuery|useSuspenseQuery)\\s*\\(",
                Pattern.CASE_INSENSITIVE);
        Pattern fetchInside = Pattern.compile(
                "(?:fetch|axios\\s*\\.\\s*(?:get|post|put|delete|patch))\\s*\\(\\s*([\"'`])(.+?)\\1",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        for (int i = 0; i < lines.length; i++) {
            if (p.matcher(lines[i]).find()) {
                // Scan current line and next 5 lines for the actual fetch call
                int end = Math.min(i + 6, lines.length);
                StringBuilder block = new StringBuilder();
                for (int j = i; j < end; j++) block.append(lines[j]).append("\n");
                Matcher fm = fetchInside.matcher(block);
                while (fm.find()) {
                    String url = collapseTemplate(fm.group(2));
                    if (!isSkippableUrl(url)) {
                        DetectedCall call = new DetectedCall();
                        call.setUrlPattern(url);
                        call.setHttpMethod(detectMethod(block.toString()));
                        call.setCallType("tanstack-query");
                        call.setFilePath(filePath);
                        call.setLineNumber(i + 1);
                        results.add(call);
                    }
                }
            }
        }
        return results;
    }

    /** SWR: useSWR('/api/users', fetcher) */
    private List<DetectedCall> detectSwr(String content, String filePath) {
        List<DetectedCall> results = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        Pattern p = Pattern.compile(
                "(?:useSWR|useSWRMutation|useSWRInfinite)\\s*\\(\\s*([\"'`])(.+?)\\1",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = p.matcher(lines[i]);
            while (m.find()) {
                String url = collapseTemplate(m.group(2));
                if (!isSkippableUrl(url)) {
                    DetectedCall call = new DetectedCall();
                    call.setUrlPattern(url);
                    call.setHttpMethod("GET");
                    call.setCallType("swr");
                    call.setFilePath(filePath);
                    call.setLineNumber(i + 1);
                    results.add(call);
                }
            }
        }
        return results;
    }

    /** Turn template body into single-line pattern; ${x} kept for normalizer */
    private static String collapseTemplate(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\s+", " ").trim();
    }

    private static boolean isSkippableUrl(String url) {
        if (url == null || url.isBlank()) return true;
        String u = url.trim();
        if (u.startsWith("./") || u.startsWith("../")) return true;
        if (u.startsWith("blob:") || u.startsWith("data:")) return true;
        // module path, not HTTP
        if (u.startsWith("@/") || u.matches("^[a-zA-Z@][a-zA-Z0-9_/@.-]*$")) return true;
        return false;
    }

    private String detectMethod(String line) {
        String lower = line.toLowerCase();
        if (lower.contains("\"method\":\"post\"") || lower.contains("method: 'post'") || lower.contains("method: \"post\""))
            return "POST";
        if (lower.contains("\"method\":\"put\"") || lower.contains("method: 'put'")) return "PUT";
        if (lower.contains("\"method\":\"delete\"") || lower.contains("method: 'delete'")) return "DELETE";
        if (lower.contains("\"method\":\"patch\"") || lower.contains("method: 'patch'")) return "PATCH";
        return "GET";
    }
}
