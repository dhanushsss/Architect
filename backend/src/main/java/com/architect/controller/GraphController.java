package com.architect.controller;

import com.architect.dto.GraphDto;
import com.architect.model.*;
import com.architect.repository.*;
import com.architect.service.GraphBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphBuilderService graphBuilderService;
    private final RepoRepository repoRepository;
    private final ComponentImportRepository componentImportRepository;
    private final RuntimeWiringWarningRepository runtimeWiringWarningRepository;

    @GetMapping
    public ResponseEntity<GraphDto> getGraph(@AuthenticationPrincipal User user) {
        GraphDto graph = graphBuilderService.buildGraph(user);
        return ResponseEntity.ok(graph);
    }

    /**
     * Ambiguous runtime wiring (e.g. Vite proxy port matching multiple repos).
     */
    @GetMapping("/warnings")
    public ResponseEntity<List<Map<String, Object>>> getWiringWarnings(@AuthenticationPrincipal User user) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RuntimeWiringWarning w : runtimeWiringWarningRepository.findByUserOrderByCreatedAtDesc(user)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", w.getId());
            m.put("repoId", w.getRepo() != null ? w.getRepo().getId() : null);
            m.put("factType", w.getFactType());
            m.put("message", w.getMessage());
            m.put("createdAt", w.getCreatedAt() != null ? w.getCreatedAt().toString() : null);
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    /**
     * Change 3 — Package-grouped tree of components for a repo.
     * GET /api/graph/tree/{repoId}
     * Returns: { "com.example.service": ["UserService", "AuthService"], ... }
     */
    @GetMapping("/tree/{repoId}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getPackageTree(
            @AuthenticationPrincipal User user,
            @PathVariable Long repoId) {
        Repo repo = repoRepository.findById(repoId)
            .filter(r -> r.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new RuntimeException("Repo not found"));

        List<ComponentImport> imports = componentImportRepository.findBySourceRepo(repo);

        // Group by package (directory path or Java package prefix)
        Map<String, List<Map<String, Object>>> tree = new LinkedHashMap<>();
        for (ComponentImport imp : imports) {
            String pkg = derivePackage(imp.getImportPath(), imp.getFilePath());
            String importType = imp.getImportType() != null ? imp.getImportType() : "EXTERNAL";
            tree.computeIfAbsent(pkg, k -> new ArrayList<>()).add(Map.of(
                "id", imp.getId(),
                "component", imp.getComponentName() != null ? imp.getComponentName() : imp.getImportPath(),
                "importPath", imp.getImportPath(),
                "filePath", imp.getFilePath(),
                "lineNumber", imp.getLineNumber() != null ? imp.getLineNumber() : 0,
                "importType", importType,
                "resolvedFile", imp.getResolvedFile() != null ? imp.getResolvedFile() : ""
            ));
        }

        // Summary counts by type
        Map<String, Long> typeCounts = imports.stream()
            .collect(Collectors.groupingBy(
                i -> i.getImportType() != null ? i.getImportType() : "EXTERNAL",
                Collectors.counting()));

        return ResponseEntity.ok(Map.of(
            "repoId", repoId,
            "repoName", repo.getName(),
            "tree", tree,
            "totalImports", imports.size(),
            "byType", typeCounts
        ));
    }

    /**
     * Change 4 — Origin trace: imports in a file + where each component is used from.
     * GET /api/graph/trace?repoId={id}&file={path}
     */
    @GetMapping("/trace")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> traceComponent(
            @AuthenticationPrincipal User user,
            @RequestParam Long repoId,
            @RequestParam String file) {
        Repo repo = repoRepository.findById(repoId)
            .filter(r -> r.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new RuntimeException("Repo not found"));

        // What this file imports
        List<ComponentImport> importsFrom = componentImportRepository.findBySourceRepoAndFilePath(repo, file);

        // What other files in this repo import from the same path (usedBy)
        List<ComponentImport> usedBy = componentImportRepository.findBySourceRepo(repo).stream()
            .filter(i -> {
                String resolved = i.getResolvedFile();
                return resolved != null && (resolved.equals(file) || resolved.startsWith(file.replaceAll("\\.[^.]+$", "")));
            })
            .toList();

        List<Map<String, Object>> importsOut = importsFrom.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId());
            m.put("component", i.getComponentName() != null ? i.getComponentName() : i.getImportPath());
            m.put("importPath", i.getImportPath());
            m.put("importType", i.getImportType() != null ? i.getImportType() : "EXTERNAL");
            m.put("resolvedFile", i.getResolvedFile() != null ? i.getResolvedFile() : "");
            m.put("lineNumber", i.getLineNumber() != null ? i.getLineNumber() : 0);
            if (i.getTargetRepo() != null) m.put("targetRepo", i.getTargetRepo().getName());
            return m;
        }).toList();

        List<Map<String, Object>> usedByOut = usedBy.stream().map(i -> Map.<String, Object>of(
            "filePath", i.getFilePath(),
            "component", i.getComponentName() != null ? i.getComponentName() : i.getImportPath(),
            "lineNumber", i.getLineNumber() != null ? i.getLineNumber() : 0
        )).toList();

        return ResponseEntity.ok(Map.of(
            "repoId", repoId,
            "file", file,
            "imports", importsOut,
            "usedBy", usedByOut
        ));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String derivePackage(String importPath, String filePath) {
        if (importPath == null) return "unknown";
        // Java: com.example.service.Foo → com.example.service
        if (importPath.contains(".") && !importPath.startsWith(".") && !importPath.startsWith("@")) {
            int last = importPath.lastIndexOf('.');
            return last > 0 ? importPath.substring(0, last) : importPath;
        }
        // JS relative: ./services/api → services
        if (importPath.startsWith("./") || importPath.startsWith("../")) {
            String dir = importPath.replaceAll("[^/]+$", "").replaceAll("^\\./", "").replaceAll("/$", "");
            return dir.isEmpty() ? "root" : dir;
        }
        // Scoped: @org/pkg or bare name
        return importPath.startsWith("@") ? importPath.split("/")[0].substring(1) : importPath.split("/")[0];
    }
}
