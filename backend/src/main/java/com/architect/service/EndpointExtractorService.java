package com.architect.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

/**
 * Extracts API endpoint definitions from source files using language-specific regex patterns.
 *
 * Architecture note: Each language is handled by a dedicated inner parser so Tree-sitter
 * AST parsers can be dropped in per-language later without touching the dispatch logic.
 */
@Slf4j
@Service
public class EndpointExtractorService {

    @Data
    public static class ExtractedEndpoint {
        private String path;
        private String httpMethod;
        private int lineNumber;
        private String framework;
        private String language;
    }

    // ── dispatch ──────────────────────────────────────────────────────────────

    public List<ExtractedEndpoint> extract(String content, String filePath) {
        return switch (detectLanguage(filePath)) {
            case "java"                         -> new SpringParser().parse(content);
            case "javascript", "typescript"     -> new NodeParser().parse(content, filePath);
            case "python"                       -> new PythonParser().parse(content);
            case "ruby"                         -> new RubyParser().parse(content);
            case "go"                           -> new GoParser().parse(content);
            default                             -> Collections.emptyList();
        };
    }

    private String detectLanguage(String filePath) {
        if (filePath.endsWith(".java"))                                  return "java";
        if (filePath.endsWith(".js") || filePath.endsWith(".mjs"))      return "javascript";
        if (filePath.endsWith(".ts") || filePath.endsWith(".tsx"))      return "typescript";
        if (filePath.endsWith(".py"))                                   return "python";
        if (filePath.endsWith(".rb"))                                   return "ruby";
        if (filePath.endsWith(".go"))                                   return "go";
        return "unknown";
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static ExtractedEndpoint ep(String method, String path, int line, String fw, String lang) {
        ExtractedEndpoint ep = new ExtractedEndpoint();
        ep.setHttpMethod(method.toUpperCase());
        ep.setPath(normalizePath(path));
        ep.setLineNumber(line);
        ep.setFramework(fw);
        ep.setLanguage(lang);
        return ep;
    }

    /** Strip trailing slash, collapse double slashes, ensure leading slash. */
    private static String normalizePath(String raw) {
        if (raw == null) return "/";
        String p = raw.trim().replaceAll("//+", "/").replaceAll("/$", "");
        return p.startsWith("/") ? p : "/" + p;
    }

    private static String joinPaths(String base, String child) {
        if (base == null || base.isBlank()) return normalizePath(child);
        return normalizePath(base + "/" + child);
    }

    // ── Spring Boot parser ────────────────────────────────────────────────────

    private static class SpringParser {

        // Class-level @RequestMapping("prefix") or @RequestMapping(value = "prefix")
        private static final Pattern CLASS_MAPPING = Pattern.compile(
            "@RequestMapping\\s*\\([^)]*?(?:value\\s*=\\s*)?[\"']([^\"']+)[\"'][^)]*?\\)",
            Pattern.MULTILINE
        );

        // Method-level @GetMapping / @PostMapping … / @RequestMapping
        // Handles: @GetMapping("/path")  @GetMapping(value="/path")  @GetMapping(path="/path")
        private static final Pattern METHOD_MAPPING = Pattern.compile(
            "@(Get|Post|Put|Delete|Patch)Mapping\\s*\\(\\s*(?:(?:value|path)\\s*=\\s*)?[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        );

        // @RequestMapping with explicit method= or defaulting to GET
        private static final Pattern REQUEST_MAPPING = Pattern.compile(
            "@RequestMapping\\s*\\([^)]*?(?:value|path)\\s*=\\s*[\"']([^\"']+)[\"'](?:[^)]*?method\\s*=\\s*RequestMethod\\.(\\w+))?",
            Pattern.CASE_INSENSITIVE
        );

        // Simple @GetMapping("/path") without named params — catches no-value form
        private static final Pattern SIMPLE_MAPPING = Pattern.compile(
            "@(Get|Post|Put|Delete|Patch)Mapping\\s*\\(\\s*[\"']([^\"']+)[\"']\\s*\\)",
            Pattern.CASE_INSENSITIVE
        );

        List<ExtractedEndpoint> parse(String content) {
            List<ExtractedEndpoint> results = new ArrayList<>();
            String[] lines = content.split("\n");

            // Find class-level path prefix (scan first occurrence of @RequestMapping before any method)
            String classPrefix = extractClassPrefix(content);

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int lineNo = i + 1;

                Matcher m = METHOD_MAPPING.matcher(line);
                boolean methodMatched = m.find();
                if (!methodMatched) {
                    m = SIMPLE_MAPPING.matcher(line);
                    methodMatched = m.find();
                }
                if (methodMatched) {
                    results.add(ep(m.group(1), joinPaths(classPrefix, m.group(2)), lineNo, "Spring Boot", "java"));
                    continue;
                }

                Matcher rm = REQUEST_MAPPING.matcher(line);
                if (rm.find()) {
                    String httpMethod = rm.groupCount() >= 2 && rm.group(2) != null ? rm.group(2) : "GET";
                    results.add(ep(httpMethod, joinPaths(classPrefix, rm.group(1)), lineNo, "Spring Boot", "java"));
                }
            }
            return results;
        }

        private String extractClassPrefix(String content) {
            // Look for @RequestMapping before the first @*Mapping method annotation
            int firstMethodMapping = findFirstMethodMapping(content);
            String searchRegion = firstMethodMapping > 0 ? content.substring(0, firstMethodMapping) : content;
            Matcher m = CLASS_MAPPING.matcher(searchRegion);
            return m.find() ? m.group(1) : "";
        }

        private int findFirstMethodMapping(String content) {
            Matcher m = Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping", Pattern.CASE_INSENSITIVE)
                               .matcher(content);
            return m.find() ? m.start() : -1;
        }
    }

    // ── Node / Express / NestJS parser ────────────────────────────────────────

    private static class NodeParser {

        // Express / Fastify / Hapi: app.get('/path'), router.post('/path'), server.route(...)
        private static final Pattern EXPRESS = Pattern.compile(
            "(?:app|router|server|fastify)\\s*\\.\\s*(get|post|put|delete|patch|options|head)\\s*\\(\\s*[\"'`]([^\"'`]+)[\"'`]",
            Pattern.CASE_INSENSITIVE
        );

        // NestJS controller decorators: @Get('/path')  @Post()  @Put(':id')
        private static final Pattern NESTJS = Pattern.compile(
            "@(Get|Post|Put|Delete|Patch|Options|Head)\\s*\\(\\s*(?:[\"'`]([^\"'`]*)[\"'`])?\\s*\\)",
            Pattern.CASE_INSENSITIVE
        );

        // Hapi: method: 'GET', path: '/path'
        private static final Pattern HAPI = Pattern.compile(
            "method\\s*:\\s*[\"']([A-Z]+)[\"'].*?path\\s*:\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        List<ExtractedEndpoint> parse(String content, String filePath) {
            List<ExtractedEndpoint> results = new ArrayList<>();
            String[] lines = content.split("\n");
            boolean isNestFile = content.contains("@Controller") || content.contains("@Module");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int lineNo = i + 1;

                if (isNestFile) {
                    Matcher nm = NESTJS.matcher(line);
                    if (nm.find()) {
                        String path = nm.groupCount() >= 2 && nm.group(2) != null ? nm.group(2) : "";
                        results.add(ep(nm.group(1), "/" + path, lineNo, "NestJS", "typescript"));
                        continue;
                    }
                }

                Matcher em = EXPRESS.matcher(line);
                if (em.find()) {
                    String fw = filePath.endsWith(".ts") ? "Express/TS" : "Express/Node";
                    results.add(ep(em.group(1), em.group(2), lineNo, fw, "javascript"));
                }
            }

            // Hapi multiline route objects — scan whole content
            Matcher hm = HAPI.matcher(content);
            while (hm.find()) {
                int lineNo = countLines(content, hm.start());
                results.add(ep(hm.group(1), hm.group(2), lineNo, "Hapi", "javascript"));
            }

            return results;
        }
    }

    // ── Python parser (Flask / FastAPI / Django) ──────────────────────────────

    private static class PythonParser {

        private static final Pattern FASTAPI = Pattern.compile(
            "@(?:app|router)\\.(get|post|put|delete|patch)\\s*\\(\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        );
        private static final Pattern FLASK_ROUTE = Pattern.compile(
            "@\\w+\\.route\\s*\\(\\s*[\"']([^\"']+)[\"'](?:.*?methods\\s*=\\s*\\[\\s*[\"']([^\"']+)[\"'])?",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        private static final Pattern DJANGO_PATH = Pattern.compile(
            "(?:re_)?path\\s*\\(\\s*[\"']([^\"']+)[\"']"
        );

        List<ExtractedEndpoint> parse(String content) {
            List<ExtractedEndpoint> results = new ArrayList<>();
            String[] lines = content.split("\n");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int lineNo = i + 1;

                Matcher fa = FASTAPI.matcher(line);
                if (fa.find()) {
                    results.add(ep(fa.group(1), fa.group(2), lineNo, "FastAPI", "python"));
                    continue;
                }

                Matcher fl = FLASK_ROUTE.matcher(line);
                if (fl.find()) {
                    String method = fl.groupCount() >= 2 && fl.group(2) != null ? fl.group(2) : "GET";
                    results.add(ep(method, fl.group(1), lineNo, "Flask", "python"));
                    continue;
                }

                // Django path() — skip if it's clearly an import line
                if (!line.contains("from django") && !line.contains("import")) {
                    Matcher dp = DJANGO_PATH.matcher(line);
                    if (dp.find()) {
                        results.add(ep("GET", dp.group(1), lineNo, "Django", "python"));
                    }
                }
            }
            return results;
        }
    }

    // ── Ruby parser (Rails / Sinatra) ─────────────────────────────────────────

    private static class RubyParser {

        private static final Pattern ROUTE = Pattern.compile(
            "^\\s*(get|post|put|delete|patch)\\s+[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        );
        // Rails resources :orders → generates standard CRUD routes
        private static final Pattern RESOURCES = Pattern.compile(
            "resources\\s+:(\\w+)"
        );

        List<ExtractedEndpoint> parse(String content) {
            List<ExtractedEndpoint> results = new ArrayList<>();
            String[] lines = content.split("\n");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int lineNo = i + 1;

                Matcher rm = ROUTE.matcher(line);
                if (rm.find()) {
                    results.add(ep(rm.group(1), rm.group(2), lineNo, "Rails/Sinatra", "ruby"));
                    continue;
                }

                // Expand resources :name into the 7 standard REST routes
                Matcher res = RESOURCES.matcher(line);
                if (res.find()) {
                    String base = "/" + res.group(1);
                    String[][] restRoutes = {
                        {"GET",    base},
                        {"POST",   base},
                        {"GET",    base + "/:id"},
                        {"PUT",    base + "/:id"},
                        {"PATCH",  base + "/:id"},
                        {"DELETE", base + "/:id"},
                    };
                    for (String[] r : restRoutes) {
                        results.add(ep(r[0], r[1], lineNo, "Rails", "ruby"));
                    }
                }
            }
            return results;
        }
    }

    // ── Go parser (Gin / Chi / net/http) ─────────────────────────────────────

    private static class GoParser {

        private static final Pattern GIN_CHI = Pattern.compile(
            "(?:r|router|gin|mux|e)\\s*\\.\\s*(GET|POST|PUT|DELETE|PATCH|Get|Post|Put|Delete|Patch|Any)\\s*\\(\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
        );
        private static final Pattern NET_HTTP = Pattern.compile(
            "http\\.HandleFunc\\s*\\(\\s*\"([^\"]+)\""
        );
        // Gorilla mux: r.HandleFunc("/path", h).Methods("GET")
        private static final Pattern GORILLA = Pattern.compile(
            "\\.HandleFunc\\s*\\(\\s*\"([^\"]+)\"[^)]*\\)\\.Methods\\s*\\(\\s*\"([^\"]+)\""
        );

        List<ExtractedEndpoint> parse(String content) {
            List<ExtractedEndpoint> results = new ArrayList<>();
            String[] lines = content.split("\n");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int lineNo = i + 1;

                Matcher gm = GIN_CHI.matcher(line);
                if (gm.find()) {
                    String method = gm.group(1).equals("Any") ? "ALL" : gm.group(1);
                    results.add(ep(method, gm.group(2), lineNo, "Go/Gin/Chi", "go"));
                    continue;
                }

                Matcher hm = NET_HTTP.matcher(line);
                if (hm.find()) {
                    results.add(ep("ALL", hm.group(1), lineNo, "net/http", "go"));
                    continue;
                }

                Matcher gm2 = GORILLA.matcher(line);
                if (gm2.find()) {
                    results.add(ep(gm2.group(2), gm2.group(1), lineNo, "Gorilla Mux", "go"));
                }
            }
            return results;
        }
    }

    // ── utility ───────────────────────────────────────────────────────────────

    private static int countLines(String content, int offset) {
        int count = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') count++;
        }
        return count;
    }
}
