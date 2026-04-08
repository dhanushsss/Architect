package com.architect.service;

import com.architect.model.ApiEndpoint;
import com.architect.model.Repo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathSegmentIndexTest {

    @Test
    void wildcardPath_matchesParameterizedSegment() {
        Repo repo = new Repo();
        repo.setId(1L);
        ApiEndpoint ep = endpoint(11L, repo, "GET", "/users/{id}");
        PathSegmentIndex index = new PathSegmentIndex(List.of(ep));

        List<ApiEndpoint> candidates = index.findCandidates("/users/123");

        assertEquals(1, candidates.size());
        assertEquals(11L, candidates.get(0).getId());
    }

    @Test
    void wildcardDepth_preventsWrongMatch() {
        Repo repo = new Repo();
        repo.setId(1L);
        ApiEndpoint ep = endpoint(12L, repo, "GET", "/users/{id}/orders");
        PathSegmentIndex index = new PathSegmentIndex(List.of(ep));

        List<ApiEndpoint> candidates = index.findCandidates("/users/orders");

        assertTrue(candidates.isEmpty());
    }

    private static ApiEndpoint endpoint(Long id, Repo repo, String method, String path) {
        ApiEndpoint ep = new ApiEndpoint();
        ep.setId(id);
        ep.setRepo(repo);
        ep.setHttpMethod(method);
        ep.setPath(path);
        ep.setFilePath("x.java");
        return ep;
    }
}
