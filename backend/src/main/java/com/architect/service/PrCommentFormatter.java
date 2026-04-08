package com.architect.service;

import com.architect.dto.AiRiskExplanation;
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
        return buildComment(scenario, impact, changedFileCount, prUrl, frontendBase, null);
    }

    public static String buildComment(Scenario scenario, ImpactDto impact, int changedFileCount,
                                      String prUrl, String frontendBase, AiRiskExplanation aiInsight) {
        int repos = impact.getDependentsCount();
        int depFiles = impact.getAffectedFiles() != null ? impact.getAffectedFiles().size() : 0;
        String api = primaryApiLine(impact);
        String impactUrl = frontendBase + "/impact/repo/" + impact.getSubjectId();

        String body = switch (scenario) {
            case WIDE_CASCADING -> formatWide(impact, repos, depFiles, api, prUrl, impactUrl, aiInsight);
            case CRITICAL_CROSS_SERVICE -> formatCritical(impact, repos, depFiles, api, prUrl, impactUrl, aiInsight);
            case MEDIUM_INTERNAL -> formatMedium(impact, repos, depFiles, api, prUrl, impactUrl, aiInsight);
            case ORPHAN_API_RISK -> formatOrphan(impact, api, prUrl, impactUrl, aiInsight);
            case SAFE_REFACTOR -> formatSafe(impact, prUrl, impactUrl, aiInsight);
        };
        return body
                + "\n\n---\n"
                + "_Zerqis tracks this prediction — if this PR is reverted or hotfixed, "
                + "accuracy data is logged automatically._\n"
                + "_React 👍 or 👎 to help calibrate future analyses._";
    }

    private static void appendWhyExact(StringBuilder sb, ImpactDto impact, AiRiskExplanation aiInsight) {
        sb.append("### Why this risk level\n\n");
        appendConcreteCallSites(sb, impact, primaryApiLine(impact));

        List<String> factors = impact.getRiskFactors();
        if (factors != null && !factors.isEmpty()) {
            for (String line : factors) {
                sb.append("- ").append(line).append("\n");
            }
        }

        appendWhatIf(sb, impact);
        appendAnalysisConfidenceLine(sb, impact);
        appendAiInsight(sb, aiInsight);
    }

    /**
     * "What if" section: shows what would need to change for the risk level to move.
     * Helps teams understand how to reduce risk for next time.
     */
    private static void appendWhatIf(StringBuilder sb, ImpactDto impact) {
        int repos = impact.getDependentsCount();
        int unresolved = impact.getUnresolvedCallCount() != null ? impact.getUnresolvedCallCount() : 0;
        int stale = impact.getStaleRepoCount() != null ? impact.getStaleRepoCount() : 0;
        Double confidence = impact.getConfidenceScore();

        List<String> tips = new java.util.ArrayList<>();

        if (stale > 0) {
            tips.add("Rescan " + stale + " stale repo(s) to raise confidence by up to 20%");
        }
        if (unresolved > 2) {
            tips.add("Resolve " + unresolved + " unresolved call(s) (dynamic URLs, env vars) — each reduces noise in the analysis");
        }
        if (confidence != null && confidence < 60 && repos == 0) {
            tips.add("Low confidence + zero dependents: this might be a false negative. Consider a deep scan before merging.");
        }
        if (repos >= 3) {
            tips.add("Consider breaking this change into smaller PRs scoped to fewer downstream consumers");
        }

        if (!tips.isEmpty()) {
            sb.append("\n<details><summary><b>How to improve this analysis</b></summary>\n\n");
            for (String tip : tips) {
                sb.append("- ").append(tip).append("\n");
            }
            sb.append("\n</details>\n\n");
        }
    }

    private static void appendAiInsight(StringBuilder sb, AiRiskExplanation e) {
        if (e == null) {
            return;
        }
        sb.append("### 🤖 AI Insight\n\n");
        sb.append("**Summary**\n");
        sb.append(e.summary()).append("\n\n");
        if (e.impact() != null && !e.impact().isEmpty()) {
            sb.append("**Impact**\n");
            for (String line : e.impact()) {
                sb.append("- ").append(line).append("\n");
            }
            sb.append("\n");
        }
        if (e.recommendations() != null && !e.recommendations().isEmpty()) {
            sb.append("**Recommended actions**\n");
            for (String line : e.recommendations()) {
                sb.append("- ").append(line).append("\n");
            }
            sb.append("\n");
        }
        if (e.confidenceNote() != null && !e.confidenceNote().isBlank()) {
            sb.append("_").append(e.confidenceNote()).append("_\n\n");
        }
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
        sb.append("\n").append(buildConfidenceBreakdown(impact)).append("\n");
    }

    static String buildConfidenceBreakdown(ImpactDto result) {
        int score = result.getConfidenceScore() == null ? 0 : result.getConfidenceScore().intValue();
        int direct = result.getDirectMatchCount() != null ? result.getDirectMatchCount() : 0;
        int inferred = result.getInferredMatchCount() != null ? result.getInferredMatchCount() : 0;
        int unresolved = result.getUnresolvedCallCount() != null ? result.getUnresolvedCallCount() : 0;
        int stale = result.getStaleRepoCount() != null ? result.getStaleRepoCount() : 0;
        int unscanned = result.getUnscannedRepoCount() != null ? result.getUnscannedRepoCount() : 0;
        int notFetched = result.getChangedFilesNotFetched() != null ? result.getChangedFilesNotFetched() : 0;

        StringBuilder sb = new StringBuilder();
        String emoji = score >= 80 ? "🟢" : score >= 50 ? "🟡" : "🔴";
        sb.append(emoji).append(" **Confidence: ").append(score).append("%**\n");

        List<String> lines = new java.util.ArrayList<>();
        if (direct > 0) {
            lines.add(direct + " direct match(es)       ● strong — exact method + path");
        }
        if (inferred > 0) {
            lines.add(inferred + " inferred match(es)     ◐ medium — wildcard/suffix match");
        }
        if (unresolved > 0) {
            lines.add(unresolved + " unresolved call(s)     ○ unknown — dynamic URL or env var");
        }
        if (stale > 0) {
            lines.add(stale + " repo(s) stale (>72h)   ○ unknown — rescan recommended");
        }
        if (unscanned > 0) {
            lines.add(unscanned + " repo(s) never scanned  ○ unknown — run deep scan");
        }
        if (notFetched > 0) {
            lines.add(notFetched + " file(s) not analyzed   ○ unknown — excluded from PR head");
        }

        if (lines.isEmpty()) {
            sb.append("└── No dependency signals detected\n");
        } else {
            for (int i = 0; i < lines.size(); i++) {
                String prefix = (i == lines.size() - 1) ? "└── " : "├── ";
                sb.append(prefix).append(lines.get(i)).append("\n");
            }
        }

        // Add legend on first appearance
        sb.append("\n<sub>● = exact match (high trust) · ◐ = fuzzy match (moderate trust) · ○ = missing data (reduces confidence)</sub>\n");
        return sb.toString();
    }

    static boolean isTrulySafe(ImpactDto result) {
        int direct = result.getDirectMatchCount() != null ? result.getDirectMatchCount() : 0;
        int unresolved = result.getUnresolvedCallCount() != null ? result.getUnresolvedCallCount() : 0;
        int stale = result.getStaleRepoCount() != null ? result.getStaleRepoCount() : 0;
        int confidence = result.getConfidenceScore() != null ? result.getConfidenceScore().intValue() : 0;
        return direct == 0 && unresolved <= 1 && stale == 0 && confidence >= 80;
    }

    private static String formatCritical(ImpactDto impact, int repos, int depFiles, String api,
                                         String prUrl, String impactUrl, AiRiskExplanation aiInsight) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🚨 Zerqis: High Risk — Cross-Service Impact\n\n");
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
        appendWhyExact(sb, impact, aiInsight);
        sb.append("---\n");
        sb.append("**[View full impact → Open in Zerqis](").append(impactUrl).append(")** · [PR](").append(prUrl).append(")\n");
        sb.append("\n<sub>Analysis by Zerqis · targeted scan + dependency graph</sub>");
        return sb.toString();
    }

    private static String formatMedium(ImpactDto impact, int repos, int depFiles, String api,
                                       String prUrl, String impactUrl, AiRiskExplanation aiInsight) {
        String name = impact.getAffectedRepos() != null && !impact.getAffectedRepos().isEmpty()
            ? impact.getAffectedRepos().get(0).getName() : "downstream service";
        StringBuilder sb = new StringBuilder();
        sb.append("## ⚠️ Zerqis: Medium Risk — Internal Dependency\n\n");
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
        appendWhyExact(sb, impact, aiInsight);
        sb.append("---\n");
        sb.append("**[Details → Open in Zerqis](").append(impactUrl).append(")** · [PR](").append(prUrl).append(")\n");
        sb.append("\n<sub>Analysis by Zerqis</sub>");
        return sb.toString();
    }

    private static String formatSafe(ImpactDto impact, String prUrl, String impactUrl, AiRiskExplanation aiInsight) {
        StringBuilder sb = new StringBuilder();
        sb.append(isTrulySafe(impact)
                ? "## ✅ Zerqis: Safe to Merge\n\n"
                : "## ✅ Zerqis: Low risk — verify manually before merging\n\n");
        sb.append("**No cross-repo impact detected** in Zerqis’s dependency graph for this change.\n\n");
        sb.append("This change appears **isolated** relative to connected services.\n\n");
        appendWhyExact(sb, impact, aiInsight);
        sb.append("---\n\n");
        sb.append("**[Open in Zerqis](").append(impactUrl).append(")** · [PR](").append(prUrl).append(")\n\n");
        sb.append("<sub>(Analysis by Zerqis)</sub>");
        return sb.toString();
    }

    private static String formatOrphan(ImpactDto impact, String api, String prUrl, String impactUrl,
                                       AiRiskExplanation aiInsight) {
        List<String> orphans = impact.getPrOrphanEndpoints();
        String show = (orphans != null && !orphans.isEmpty()) ? orphans.get(0) : api;
        StringBuilder sb = new StringBuilder();
        sb.append("## ⚠️ Zerqis: Risk — No Known Consumers\n\n");
        sb.append("This API has **no detected cross-repo callers** in Zerqis’s graph.\n\n");
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
        appendWhyExact(sb, impact, aiInsight);
        sb.append("---\n");
        sb.append("**[Investigate → Open in Zerqis](").append(impactUrl).append(")** · [PR](").append(prUrl).append(")\n");
        sb.append("\n<sub>Analysis by Zerqis</sub>");
        return sb.toString();
    }

    private static String formatWide(ImpactDto impact, int repos, int depFiles, String api,
                                     String prUrl, String impactUrl, AiRiskExplanation aiInsight) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🚨 Zerqis: High Risk — Wide System Impact\n\n");
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
        appendWhyExact(sb, impact, aiInsight);
        sb.append("---\n");
        sb.append("**[View impact → Open in Zerqis](").append(impactUrl).append(")** · [PR](").append(prUrl).append(")\n");
        sb.append("\n<sub>Analysis by Zerqis</sub>");
        return sb.toString();
    }

    private static void listRepos(StringBuilder sb, List<ImpactDto.AffectedItem> repos, int max) {
        if (repos == null || repos.isEmpty()) {
            sb.append("_See Zerqis for details._\n");
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
