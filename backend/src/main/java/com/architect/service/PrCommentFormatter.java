package com.architect.service;

import com.architect.dto.ImpactDto;

import java.util.List;

/**
 * Five PR comment scenarios — human language, verdict first, why it matters, actionable CTA.
 */
public final class PrCommentFormatter {

    public enum Scenario {
        /** Multiple services, high blast radius (2–3 repos) */
        CRITICAL_CROSS_SERVICE,
        /** Single downstream repo */
        MEDIUM_INTERNAL,
        /** No API surface or no cross-repo deps */
        SAFE_REFACTOR,
        /** API touched but no tracked callers — dead code vs hidden deps */
        ORPHAN_API_RISK,
        /** 4+ repos or very wide file fan-out */
        WIDE_CASCADING
    }

    private PrCommentFormatter() {}

    public static Scenario classify(ImpactDto impact) {
        int repos = impact.getDependentsCount();
        int files = impact.getAffectedFiles() != null ? impact.getAffectedFiles().size() : 0;
        List<String> changed = impact.getChangedEndpoints();
        boolean apiTouched = changed != null && !changed.isEmpty();

        if (repos >= 4 || (repos >= 3 && files >= 10)) {
            return Scenario.WIDE_CASCADING;
        }
        if (repos >= 2) {
            return Scenario.CRITICAL_CROSS_SERVICE;
        }
        if (repos == 1) {
            return Scenario.MEDIUM_INTERNAL;
        }
        if (repos == 0 && apiTouched) {
            return Scenario.ORPHAN_API_RISK;
        }
        return Scenario.SAFE_REFACTOR;
    }

    public static String primaryApiLine(ImpactDto impact) {
        List<String> eps = impact.getChangedEndpoints();
        if (eps != null && !eps.isEmpty()) {
            return eps.get(0);
        }
        List<String> orphan = impact.getPrOrphanEndpoints();
        if (orphan != null && !orphan.isEmpty()) {
            return orphan.get(0);
        }
        return "API routes in this PR";
    }

    public static String buildComment(Scenario scenario, ImpactDto impact, int changedFileCount,
                                      String prUrl, String frontendBase) {
        int repos = impact.getDependentsCount();
        int depFiles = impact.getAffectedFiles() != null ? impact.getAffectedFiles().size() : 0;
        String api = primaryApiLine(impact);
        String impactUrl = frontendBase + "/impact/repo/" + impact.getSubjectId();

        return switch (scenario) {
            case WIDE_CASCADING -> formatWide(impact, repos, depFiles, api, prUrl, impactUrl);
            case CRITICAL_CROSS_SERVICE -> formatCritical(impact, repos, depFiles, api, prUrl, impactUrl);
            case MEDIUM_INTERNAL -> formatMedium(impact, repos, depFiles, api, prUrl, impactUrl);
            case ORPHAN_API_RISK -> formatOrphan(impact, api, prUrl, impactUrl);
            case SAFE_REFACTOR -> formatSafe(impact, prUrl, impactUrl);
        };
    }

    private static void appendWhyExact(StringBuilder sb, ImpactDto impact) {
        sb.append("### Why this is risky\n\n");
        appendConcreteCallSites(sb, impact, primaryApiLine(impact));

        List<String> factors = impact.getRiskFactors();
        if (factors != null && !factors.isEmpty()) {
            for (String line : factors) {
                sb.append("- ").append(line).append("\n");
            }
        }
        appendAnalysisConfidenceLine(sb, impact);
    }

    private static void appendConcreteCallSites(StringBuilder sb, ImpactDto impact, String api) {
        List<ImpactDto.AffectedItem> files = impact.getAffectedFiles();
        if (files == null || files.isEmpty()) {
            return;
        }
        int max = Math.min(files.size(), 4);
        for (int i = 0; i < max; i++) {
            ImpactDto.AffectedItem f = files.get(i);
            String repoAndLine = f.getDetail() != null ? f.getDetail() : "unknown";
            String callSite = f.getName() != null ? f.getName() : "unknown-file";
            sb.append("- ").append(repoAndLine).append(" calls `").append(api).append("` (")
                    .append(callSite).append(")\n");
        }
        if (files.size() > max) {
            sb.append("- ...and ").append(files.size() - max).append(" more call site(s)\n");
        }
        sb.append("\n");
    }

    private static void appendAnalysisConfidenceLine(StringBuilder sb, ImpactDto impact) {
        if (impact.getConfidenceScore() == null) {
            return;
        }
        int conf = impact.getConfidenceScore().intValue();
        int unresolved = impact.getUnresolvedCallCount() != null ? impact.getUnresolvedCallCount() : 0;
        int stale = impact.getStaleRepoCount() != null ? impact.getStaleRepoCount() : 0;
        int unscanned = impact.getUnscannedRepoCount() != null ? impact.getUnscannedRepoCount() : 0;
        int notFetched = impact.getChangedFilesNotFetched() != null ? impact.getChangedFilesNotFetched() : 0;

        sb.append("\n**Confidence:** ").append(conf).append("%\n");
        if (unresolved > 0 || stale > 0 || unscanned > 0 || notFetched > 0) {
            sb.append("**Unknowns:**\n");
            if (unresolved > 0) {
                sb.append("- ⚠️ ").append(unresolved).append(" API call(s) could not be resolved\n");
            }
            if (stale > 0) {
                sb.append("- ⚠️ ").append(stale).append(" repo(s) have stale data (>48h)\n");
            }
            if (unscanned > 0) {
                sb.append("- ⚠️ ").append(unscanned).append(" repo(s) have not been deeply scanned yet\n");
            }
            if (notFetched > 0) {
                sb.append("- ⚠️ ").append(notFetched).append(" changed file(s) could not be fetched at PR head\n");
            }
        }
        if (stale > 0 || unscanned > 0) {
            sb.append("\n⚠️ Data may be stale. Re-run deep scan before merge-critical decisions.\n");
        }
        sb.append("\n");
    }

    private static String formatCritical(ImpactDto impact, int repos, int depFiles, String api,
                                         String prUrl, String impactUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🚨 Architect: High Risk — Cross-Service Impact\n\n");
        sb.append("This change **affects multiple services** and may break production flows.\n\n");
        sb.append("**Impact**\n");
        sb.append("• **").append(repos).append("** ").append(repos == 1 ? "repository" : "repositories").append(" affected\n");
        sb.append("• **").append(Math.max(depFiles, 1)).append("** dependent file").append(depFiles == 1 ? "" : "s").append("\n\n");
        sb.append("**Affected services**\n");
        listRepos(sb, impact.getAffectedRepos(), 12);
        sb.append("\n**Why this matters**\n");
        sb.append("You modified API → **`").append(api).append("`**  \n");
        sb.append("This endpoint is **actively used** across these services.\n\n");
        sb.append("⚠️ **Action required:** Coordinate with affected teams or update dependent calls **before merging**.\n\n");
        appendWhyExact(sb, impact);
        sb.append("---\n");
        sb.append("**[View full impact → Open in Architect](").append(impactUrl).append(")** · [PR](").append(prUrl).append(")\n");
        sb.append("\n<sub>Analysis by Architect · targeted scan + dependency graph</sub>");
        return sb.toString();
    }

    private static String formatMedium(ImpactDto impact, int repos, int depFiles, String api,
                                       String prUrl, String impactUrl) {
        String name = impact.getAffectedRepos() != null && !impact.getAffectedRepos().isEmpty()
            ? impact.getAffectedRepos().get(0).getName() : "downstream service";
        StringBuilder sb = new StringBuilder();
        sb.append("## ⚠️ Architect: Medium Risk — Internal Dependency\n\n");
        sb.append("This change impacts **another part** of your system (limited blast radius).\n\n");
        sb.append("**Impact**\n");
        sb.append("• **1** repository affected\n");
        sb.append("• **").append(Math.max(depFiles, 1)).append("** dependent file").append(depFiles == 1 ? "" : "s").append("\n\n");
        sb.append("**Affected**\n");
        sb.append("- **").append(name).append("**\n\n");
        sb.append("**Why**\n");
        sb.append("Changes detected in API → **`").append(api).append("`**  \n");
        sb.append("Used within your connected services.\n\n");
        sb.append("⚠️ **Action:** Quick validation recommended before merge.\n\n");
        appendWhyExact(sb, impact);
        sb.append("---\n");
        sb.append("**[Details → Open in Architect](").append(impactUrl).append(")** · [PR](").append(prUrl).append(")\n");
        sb.append("\n<sub>Analysis by Architect</sub>");
        return sb.toString();
    }

    private static String formatSafe(ImpactDto impact, String prUrl, String impactUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ✅ Architect: Safe to Merge\n\n");
        sb.append("**No cross-repo impact detected** in Architect’s dependency graph for this change.\n\n");
        sb.append("This change appears **isolated** relative to connected services.\n\n");
        appendWhyExact(sb, impact);
        sb.append("---\n\n");
        sb.append("**[Open in Architect](").append(impactUrl).append(")** · [PR](").append(prUrl).append(")\n\n");
        sb.append("<sub>(Analysis by Architect)</sub>");
        return sb.toString();
    }

    private static String formatOrphan(ImpactDto impact, String api, String prUrl, String impactUrl) {
        List<String> orphans = impact.getPrOrphanEndpoints();
        String show = (orphans != null && !orphans.isEmpty()) ? orphans.get(0) : api;
        StringBuilder sb = new StringBuilder();
        sb.append("## ⚠️ Architect: Risk — No Known Consumers\n\n");
        sb.append("This API has **no detected cross-repo callers** in Architect’s graph.\n\n");
        sb.append("**API**\n");
        sb.append("`").append(show).append("`\n\n");
        if (orphans != null && orphans.size() > 1) {
            sb.append("_Also touched:_ ");
            orphans.stream().skip(1).limit(5).forEach(o -> sb.append("`").append(o).append("` "));
            sb.append("\n\n");
        }
        sb.append("**Why this matters**\n");
        sb.append("- This may be **unused** (safe to remove), **or**\n");
        sb.append("- Calls are **not tracked** (dynamic URLs, mobile, external clients) → **hidden dependency risk**\n\n");
        sb.append("⚠️ **Action:** Confirm usage before removing or materially changing this API.\n\n");
        appendWhyExact(sb, impact);
        sb.append("---\n");
        sb.append("**[Investigate → Open in Architect](").append(impactUrl).append(")** · [PR](").append(prUrl).append(")\n");
        sb.append("\n<sub>Analysis by Architect</sub>");
        return sb.toString();
    }

    private static String formatWide(ImpactDto impact, int repos, int depFiles, String api,
                                     String prUrl, String impactUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🚨 Architect: High Risk — Wide System Impact\n\n");
        sb.append("This change has a **large blast radius** across your system.\n\n");
        sb.append("**Impact**\n");
        sb.append("• **").append(repos).append("** repositories affected\n");
        sb.append("• **").append(Math.max(depFiles, 1)).append("** dependent files\n\n");
        sb.append("⚠️ **This is a high-risk change.**\n\n");
        sb.append("**Why**\n");
        sb.append("Core API modified → **`").append(api).append("`**  \n");
        sb.append("Used across **multiple services**.\n\n");
        sb.append("**Recommended**\n");
        sb.append("- Validate integration flows end-to-end\n");
        sb.append("- Consider **phased rollout** or feature flags\n\n");
        sb.append("**Affected services**\n");
        listRepos(sb, impact.getAffectedRepos(), 15);
        sb.append("\n");
        appendWhyExact(sb, impact);
        sb.append("---\n");
        sb.append("**[View impact → Open in Architect](").append(impactUrl).append(")** · [PR](").append(prUrl).append(")\n");
        sb.append("\n<sub>Analysis by Architect</sub>");
        return sb.toString();
    }

    private static void listRepos(StringBuilder sb, List<ImpactDto.AffectedItem> repos, int max) {
        if (repos == null || repos.isEmpty()) {
            sb.append("_See Architect for details._\n");
            return;
        }
        int n = Math.min(repos.size(), max);
        for (int i = 0; i < n; i++) {
            sb.append("- **").append(repos.get(i).getName()).append("**\n");
        }
        if (repos.size() > max) {
            sb.append("- _…and ").append(repos.size() - max).append(" more_\n");
        }
    }
}
