package com.architect.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

@Slf4j
@Service
public class ImportTracerService {

    public enum ImportType { INTERNAL, MONOREPO, EXTERNAL }

    @Data
    public static class DetectedImport {
        private String importPath;
        private String componentName;
        private String filePath;
        private int lineNumber;
        private ImportType importType;
        private String resolvedFile; // for INTERNAL imports
    }

    /**
     * Detect imports and classify each as INTERNAL / MONOREPO / EXTERNAL.
     *
     * @param content    raw file content
     * @param filePath   path of the file being scanned (used for relative resolution)
     * @param repoName   name of the current repo (e.g. "my-service")
     * @param knownRepos set of all repo names/full-names in the org for MONOREPO detection
     */
    public List<DetectedImport> detect(String content, String filePath,
                                       String repoName, Set<String> knownRepos) {
        List<DetectedImport> results = new ArrayList<>();
        String[] lines = content.split("\n");

        if (filePath.endsWith(".js") || filePath.endsWith(".ts") ||
            filePath.endsWith(".jsx") || filePath.endsWith(".tsx") || filePath.endsWith(".vue")) {
            results.addAll(detectJsImports(lines, filePath, repoName, knownRepos));
        } else if (filePath.endsWith(".java")) {
            results.addAll(detectJavaImports(lines, filePath, repoName, knownRepos));
        } else if (filePath.endsWith(".py")) {
            results.addAll(detectPythonImports(lines, filePath, repoName, knownRepos));
        }
        return results;
    }

    /** Backward-compat overload — classifies everything as EXTERNAL (no org context). */
    public List<DetectedImport> detect(String content, String filePath) {
        return detect(content, filePath, "", Set.of());
    }

    // -----------------------------------------------------------------------
    // JS / TS / JSX / TSX / Vue
    // -----------------------------------------------------------------------

    private List<DetectedImport> detectJsImports(String[] lines, String filePath,
                                                  String repoName, Set<String> knownRepos) {
        List<DetectedImport> results = new ArrayList<>();
        // Matches: scoped (@org/pkg), bare package names, AND relative paths (./foo, ../bar)
        Pattern p = Pattern.compile(
            "import\\s+.*?\\s+from\\s+[\"'`]([^\"'`]+)[\"'`]|" +
            "require\\s*\\(\\s*[\"'`]([^\"'`]+)[\"'`]\\s*\\)"
        );
        for (int i = 0; i < lines.length; i++) {
            Matcher m = p.matcher(lines[i]);
            while (m.find()) {
                String path = m.group(1) != null ? m.group(1) : m.group(2);
                if (path == null || path.isEmpty()) continue;

                DetectedImport di = new DetectedImport();
                di.setImportPath(path);
                di.setComponentName(extractComponentName(lines[i]));
                di.setFilePath(filePath);
                di.setLineNumber(i + 1);
                di.setImportType(classifyJs(path, repoName, knownRepos));

                if (di.getImportType() == ImportType.INTERNAL) {
                    di.setResolvedFile(resolveRelative(filePath, path));
                }
                results.add(di);
            }
        }
        return results;
    }

    private ImportType classifyJs(String importPath, String repoName, Set<String> knownRepos) {
        if (importPath.startsWith("./") || importPath.startsWith("../")) {
            return ImportType.INTERNAL;
        }
        // Scoped package: check if scope or package name matches a known repo
        if (importPath.startsWith("@")) {
            String pkg = importPath.substring(1); // strip @
            for (String known : knownRepos) {
                String baseName = known.contains("/") ? known.split("/")[1] : known;
                if (pkg.startsWith(baseName) || pkg.contains(baseName)) {
                    return ImportType.MONOREPO;
                }
            }
        }
        // Bare name matching a known repo
        for (String known : knownRepos) {
            String baseName = known.contains("/") ? known.split("/")[1] : known;
            if (importPath.equals(baseName) || importPath.startsWith(baseName + "/")) {
                return ImportType.MONOREPO;
            }
        }
        return ImportType.EXTERNAL;
    }

    // -----------------------------------------------------------------------
    // Java
    // -----------------------------------------------------------------------

    private List<DetectedImport> detectJavaImports(String[] lines, String filePath,
                                                    String repoName, Set<String> knownRepos) {
        List<DetectedImport> results = new ArrayList<>();
        Pattern p = Pattern.compile("^import\\s+([a-zA-Z][a-zA-Z0-9.]+);");
        for (int i = 0; i < lines.length; i++) {
            Matcher m = p.matcher(lines[i].trim());
            if (m.find()) {
                String importPath = m.group(1);
                if (importPath.startsWith("java.") || importPath.startsWith("javax.")
                    || importPath.startsWith("org.springframework")
                    || importPath.startsWith("lombok")) continue;

                DetectedImport di = new DetectedImport();
                di.setImportPath(importPath);
                di.setComponentName(importPath.substring(importPath.lastIndexOf('.') + 1));
                di.setFilePath(filePath);
                di.setLineNumber(i + 1);
                di.setImportType(classifyJava(importPath, repoName, knownRepos));
                results.add(di);
            }
        }
        return results;
    }

    private ImportType classifyJava(String importPath, String repoName, Set<String> knownRepos) {
        // Same top-level package = INTERNAL
        String[] parts = importPath.split("\\.");
        String[] repoParts = repoName.replace("-", ".").split("\\.");
        if (parts.length >= 2 && repoParts.length >= 2 && parts[1].equals(repoParts[0])) {
            return ImportType.INTERNAL;
        }
        for (String known : knownRepos) {
            String baseName = known.contains("/") ? known.split("/")[1] : known;
            if (importPath.contains(baseName.replace("-", "."))) {
                return ImportType.MONOREPO;
            }
        }
        return ImportType.EXTERNAL;
    }

    // -----------------------------------------------------------------------
    // Python
    // -----------------------------------------------------------------------

    private List<DetectedImport> detectPythonImports(String[] lines, String filePath,
                                                      String repoName, Set<String> knownRepos) {
        List<DetectedImport> results = new ArrayList<>();
        Pattern fromImport = Pattern.compile("from\\s+([a-zA-Z_.][a-zA-Z0-9_.]+)\\s+import\\s+(.+)");
        Pattern regularImport = Pattern.compile("^import\\s+([a-zA-Z_][a-zA-Z0-9_.]+)");
        for (int i = 0; i < lines.length; i++) {
            Matcher fm = fromImport.matcher(lines[i]);
            if (fm.find()) {
                String pkg = fm.group(1);
                if (!isStdLibPython(pkg)) {
                    DetectedImport di = new DetectedImport();
                    di.setImportPath(pkg);
                    di.setComponentName(fm.group(2).trim().split(",")[0].trim());
                    di.setFilePath(filePath);
                    di.setLineNumber(i + 1);
                    di.setImportType(classifyPython(pkg, repoName, knownRepos));
                    if (pkg.startsWith(".")) di.setResolvedFile(resolveRelative(filePath, pkg));
                    results.add(di);
                }
                continue;
            }
            Matcher rm = regularImport.matcher(lines[i].trim());
            if (rm.find()) {
                String pkg = rm.group(1);
                if (!isStdLibPython(pkg)) {
                    DetectedImport di = new DetectedImport();
                    di.setImportPath(pkg);
                    di.setComponentName(pkg);
                    di.setFilePath(filePath);
                    di.setLineNumber(i + 1);
                    di.setImportType(classifyPython(pkg, repoName, knownRepos));
                    results.add(di);
                }
            }
        }
        return results;
    }

    private ImportType classifyPython(String pkg, String repoName, Set<String> knownRepos) {
        if (pkg.startsWith(".")) return ImportType.INTERNAL; // relative import
        String topLevel = pkg.split("\\.")[0];
        if (topLevel.equals(repoName.replace("-", "_"))) return ImportType.INTERNAL;
        for (String known : knownRepos) {
            String baseName = (known.contains("/") ? known.split("/")[1] : known).replace("-", "_");
            if (topLevel.equals(baseName)) return ImportType.MONOREPO;
        }
        return ImportType.EXTERNAL;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Resolve a relative JS/Python import path against the importing file's directory. */
    private String resolveRelative(String sourceFile, String relativePath) {
        String dir = sourceFile.contains("/")
            ? sourceFile.substring(0, sourceFile.lastIndexOf('/'))
            : "";
        String[] parts = relativePath.split("/");
        List<String> segments = new ArrayList<>(Arrays.asList(dir.split("/")));
        for (String part : parts) {
            if ("..".equals(part)) {
                if (!segments.isEmpty()) segments.remove(segments.size() - 1);
            } else if (!".".equals(part) && !part.isEmpty()) {
                segments.add(part);
            }
        }
        return String.join("/", segments);
    }

    private boolean isStdLibPython(String pkg) {
        Set<String> stdlib = Set.of("os", "sys", "re", "json", "math", "time", "datetime",
            "pathlib", "typing", "collections", "itertools", "functools", "io", "abc",
            "dataclasses", "enum", "logging", "unittest", "threading", "asyncio", "urllib");
        return stdlib.contains(pkg.split("\\.")[0]);
    }

    private String extractComponentName(String line) {
        Pattern p = Pattern.compile("import\\s+\\{([^}]+)\\}");
        Matcher m = p.matcher(line);
        if (m.find()) return m.group(1).trim().split(",")[0].trim();
        Pattern p2 = Pattern.compile("import\\s+(\\w+)\\s+from");
        Matcher m2 = p2.matcher(line);
        if (m2.find()) return m2.group(1);
        return "unknown";
    }
}
