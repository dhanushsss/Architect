package com.architect.service;

import com.architect.config.InstanceIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Polls the DB-backed queue and hands work to {@link ScanJobAsyncExecutor} on {@code scanExecutor}.
 */
@Component
@RequiredArgsConstructor
public class ScanWorkerService {

    private final ScanQueueService scanQueueService;
    private final ScanJobAsyncExecutor scanJobAsyncExecutor;
    private final InstanceIdentity instanceIdentity;

    @Scheduled(fixedDelay = 200)
    public void pollQueue() {
        scanQueueService.claimNext(instanceIdentity.getId(), Instant.now())
                .ifPresent(scanJobAsyncExecutor::executeScanJob);
    }
}
