package com.architect.service;

import com.architect.dto.ImpactDto;
import org.junit.jupiter.api.Test;

import java.util.List;

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

        assertTrue(out.contains("**Confidence:** 72%"));
        assertTrue(out.contains("2 API call(s) could not be resolved"));
        assertTrue(out.contains("1 repo(s) have stale data (>48h)"));
        assertTrue(out.contains("Data may be stale"));
    }
}

