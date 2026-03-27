package com.architect.service;

import com.architect.dto.ImpactDto;
import com.architect.model.PrPrediction;
import com.architect.repository.PrPredictionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PRAnalysisServiceLogicTest {

    @Test
    void logPrediction_savesRecord_whenCalled() throws Exception {
        AtomicInteger saveCalls = new AtomicInteger();
        PrPrediction[] captured = new PrPrediction[1];
        PrPredictionRepository repo = proxyPredictionRepository(args -> {
            saveCalls.incrementAndGet();
            captured[0] = (PrPrediction) args[0];
            return captured[0];
        });

        PRAnalysisService service = new PRAnalysisService(
                null, null, null, null, null, null, repo, null, new ObjectMapper(), null);

        ImpactDto impact = ImpactDto.builder()
                .verdict("SAFE TO MERGE")
                .confidenceScore(91.0)
                .directMatchCount(0)
                .inferredMatchCount(1)
                .unresolvedCallCount(0)
                .staleRepoCount(0)
                .affectedRepos(List.of(ImpactDto.AffectedItem.builder().id("1").name("svc-a").type("REPO").build()))
                .build();

        invokeLogPrediction(service, 123, "owner/repo", "abc123", impact);

        assertEquals(1, saveCalls.get());
        assertNotNull(captured[0]);
        assertEquals("owner/repo", captured[0].getRepoFullName());
        assertEquals(123, captured[0].getPrNumber());
    }

    @Test
    void logPrediction_swallowSaveFailure_andDoesNotThrow() throws Exception {
        PrPredictionRepository repo = proxyPredictionRepository(args -> {
            throw new RuntimeException("db unavailable");
        });
        PRAnalysisService service = new PRAnalysisService(
                null, null, null, null, null, null, repo, null, new ObjectMapper(), null);

        ImpactDto impact = ImpactDto.builder()
                .verdict("BLOCKED")
                .confidenceScore(70.0)
                .directMatchCount(2)
                .inferredMatchCount(1)
                .unresolvedCallCount(3)
                .staleRepoCount(1)
                .build();

        assertDoesNotThrow(() -> invokeLogPrediction(service, 124, "owner/repo", "def456", impact));
    }

    private interface SaveHandler {
        Object onSave(Object[] args);
    }

    private static PrPredictionRepository proxyPredictionRepository(SaveHandler handler) {
        return (PrPredictionRepository) Proxy.newProxyInstance(
                PrPredictionRepository.class.getClassLoader(),
                new Class[]{PrPredictionRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("save".equals(name)) {
                        return handler.onSave(args);
                    }
                    if ("toString".equals(name)) return "ProxyPrPredictionRepository";
                    if ("hashCode".equals(name)) return 1;
                    if ("equals".equals(name)) return proxy == args[0];
                    if ("findAll".equals(name)) return List.of();
                    if ("count".equals(name)) return 0L;
                    return null;
                });
    }

    private static void invokeLogPrediction(PRAnalysisService service, int prNumber, String repoFullName,
                                            String headSha, ImpactDto impact) throws Exception {
        Method m = PRAnalysisService.class.getDeclaredMethod(
                "logPrediction", int.class, String.class, String.class, ImpactDto.class);
        m.setAccessible(true);
        try {
            m.invoke(service, prNumber, repoFullName, headSha, impact);
        } catch (InvocationTargetException e) {
            throw (e.getCause() instanceof Exception ex) ? ex : e;
        }
    }
}
