package com.architect.service;

import com.architect.dto.AiRiskExplanation;
import com.architect.dto.ImpactDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrCommentFormatterTest {

    @Test
    void commentIncludesConfidenceAndUnknowns() {
        ImpactDto impact = ImpactDto.builder()
                .subjectId("1")
                .dependentsCount(2)
                .affectedFiles(List.of(
                        ImpactDto.AffectedItem.builder().id("1").name("a").type("FILE").detail("x").build()))
                .affectedRepos(List.of(
                        ImpactDto.AffectedItem.builder().id("2").name("frontend").type("REPO").detail("x").build()))
                .changedEndpoints(List.of("GET /api/users/profile"))
                .confidenceScore(72.0)
                .directMatchCount(2)
                .inferredMatchCount(1)
                .unresolvedCallCount(2)
                .staleRepoCount(1)
                .changedFilesNotFetched(1)
                .build();

        String out = PrCommentFormatter.buildComment(
                PrCommentFormatter.classify(impact),
                impact,
                3,
                "https://example/pr/1",
                "http://localhost:3000");

        assertTrue(out.contains("**Confidence: 72%**"));
        assertTrue(out.contains("2 direct match(es)"));
        assertTrue(out.contains("1 inferred match(es)"));
        assertTrue(out.contains("2 unresolved call(s)"));
        assertTrue(out.contains("1 repo(s) stale (>72h)"));
    }

    @Test
    void commentIncludesAiInsightWhenProvided() {
        ImpactDto impact = ImpactDto.builder()
                .subjectId("1")
                .dependentsCount(1)
                .affectedRepos(List.of(
                        ImpactDto.AffectedItem.builder().id("2").name("svc-a").type("REPO").detail("").build()))
                .changedEndpoints(List.of("GET /api/x"))
                .confidenceScore(80.0)
                .verdict("REVIEW REQUIRED")
                .build();

        AiRiskExplanation ai = new AiRiskExplanation(
                "Summary line for tests.",
                List.of("Impact bullet one."),
                List.of("Do something concrete."),
                "Uncertainty note.");

        String out = PrCommentFormatter.buildComment(
                PrCommentFormatter.classify(impact),
                impact,
                2,
                "https://example/pr/2",
                "http://localhost:3000",
                ai);

        assertTrue(out.contains("### 🤖 AI Insight"));
        assertTrue(out.contains("**Summary**"));
        assertTrue(out.contains("Summary line for tests."));
        assertTrue(out.contains("**Impact**"));
        assertTrue(out.contains("- Impact bullet one."));
        assertTrue(out.contains("**Recommended actions**"));
        assertTrue(out.contains("- Do something concrete."));
        assertTrue(out.contains("_Uncertainty note._"));
    }

    @Test
    void commentOmitsAiSectionWhenNull() {
        ImpactDto impact = ImpactDto.builder()
                .subjectId("1")
                .dependentsCount(0)
                .changedEndpoints(List.of())
                .verdict("SAFE TO MERGE")
                .build();
        String out = PrCommentFormatter.buildComment(
                PrCommentFormatter.classify(impact),
                impact,
                0,
                "https://example/pr/3",
                "http://localhost:3000",
                null);
        assertFalse(out.contains("### 🤖 AI Insight"));
        assertTrue(out.contains("Was this useful? React 👍 or 👎 to this comment"));
    }
}

